/**
   Copyright [2013] [Mushroom]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
/**
 Notice : this source is extracted from Hadoop metric2 package
 and some source code may changed by zavakid
 */
package com.zavakid.mushroom.impl;

import java.util.HashMap;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.zavakid.mushroom.Metric;
import com.zavakid.mushroom.MetricsFilter;
import com.zavakid.mushroom.MetricsSource;
import com.zavakid.mushroom.MetricsTag;
import com.zavakid.mushroom.util.Contracts;
import com.zavakid.mushroom.util.MBeans;

/**
 * An adapter class for metrics source and associated filter and jmx impl
 * 
 * @author Hadoop metric2 package's authors
 * @author zavakid 2013 2013-4-4 下午5:44:08
 * @since 0.1
 */
public class MetricsSourceAdapter implements DynamicMBean {

    private static final Log                 LOG = LogFactory.getLog(MetricsSourceAdapter.class);

    private final String                     prefix, name;
    private final MetricsSource              source;
    private final MetricsFilter              recordFilter, metricFilter;
    private final HashMap<String, Attribute> attrCache;
    private final MBeanInfoBuilder           infoBuilder;
    private final Iterable<MetricsTag>       injectedTags;

    private Iterable<MetricsRecordImpl>      lastRecs;
    private long                             jmxCacheTS;
    private int                              jmxCacheTTL;
    private MBeanInfo                        infoCache;
    private ObjectName                       mbeanName;

    MetricsSourceAdapter(String prefix, String name, String description, MetricsSource source,
                         Iterable<MetricsTag> injectedTags, MetricsFilter recordFilter, MetricsFilter metricFilter,
                         int jmxCacheTTL){
        this.prefix = Contracts.checkNotNull(prefix, "prefix");
        this.name = Contracts.checkNotNull(name, "name");
        this.source = Contracts.checkNotNull(source, "source");
        attrCache = new HashMap<String, Attribute>();
        infoBuilder = new MBeanInfoBuilder(name, description);
        this.injectedTags = injectedTags;
        this.recordFilter = recordFilter;
        this.metricFilter = metricFilter;
        this.jmxCacheTTL = Contracts.checkArg(jmxCacheTTL, jmxCacheTTL > 0, "jmxCacheTTL");
    }

    MetricsSourceAdapter(String prefix, String name, String description, MetricsSource source,
                         Iterable<MetricsTag> injectedTags, int period, MetricsConfig conf){
        this(prefix, name, description, source, injectedTags, conf.getFilter(MetricsConfig.RECORD_FILTER_KEY),
             conf.getFilter(MetricsConfig.METRIC_FILTER_KEY), period);
    }

    void start() {
        if (mbeanName != null) {
            LOG.warn("MBean Source " + name + " already initialized!");
        }
        mbeanName = MBeans.register(prefix, name, this);
        LOG.info("MBean for source " + name + " registered.");
        LOG.debug("Stacktrace: " + new Throwable());
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        updateJmxCache();
        synchronized (this) {
            Attribute a = attrCache.get(attribute);
            if (a == null) {
                throw new AttributeNotFoundException(attribute + " not found");
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(attribute + ": " + a.getName() + "=" + a.getValue());
            }
            return a.getValue();
        }
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException,
                                                 MBeanException, ReflectionException {
        throw new UnsupportedOperationException("Metrics are read-only.");
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        updateJmxCache();
        synchronized (this) {
            AttributeList ret = new AttributeList();
            for (String key : attributes) {
                Attribute attr = attrCache.get(key);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(key + ": " + attr.getName() + "=" + attr.getValue());
                }
                ret.add(attr);
            }
            return ret;
        }
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        throw new UnsupportedOperationException("Metrics are read-only.");
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException,
                                                                                ReflectionException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        updateJmxCache();
        return infoCache;
    }

    private void updateJmxCache() {
        boolean getAllMetrics = false;
        synchronized (this) {
            if (System.currentTimeMillis() - jmxCacheTS >= jmxCacheTTL) {
                // temporarilly advance the expired while updating the cache
                jmxCacheTS = System.currentTimeMillis() + jmxCacheTTL;
                if (lastRecs == null) {
                    getAllMetrics = true;
                }
            } else {
                return;
            }
        }

        if (getAllMetrics) {
            MetricsBuilderImpl builder = new MetricsBuilderImpl();
            getMetrics(builder, true);
        }

        synchronized (this) {
            int cacheSize = attrCache.size(); // because updateAttrCache changes it!
            int numMetrics = updateAttrCache();
            if (cacheSize < numMetrics) {
                updateInfoCache();
            }
            jmxCacheTS = System.currentTimeMillis();
            lastRecs = null;
        }
    }

    Iterable<MetricsRecordImpl> getMetrics(MetricsBuilderImpl builder, boolean all) {
        builder.setRecordFilter(recordFilter).setMetricFilter(metricFilter);
        synchronized (this) {
            if (lastRecs == null) {
                all = true; // Get all the metrics to populate the sink caches
            }
        }
        source.getMetrics(builder, all);
        for (MetricsRecordBuilderImpl rb : builder) {
            for (MetricsTag t : injectedTags) {
                rb.add(t);
            }
        }
        synchronized (this) {
            lastRecs = builder.getRecords();
            return lastRecs;
        }
    }

    synchronized void stop() {
        MBeans.unregister(mbeanName);
        mbeanName = null;
    }

    synchronized void refreshMBean() {
        MBeans.unregister(mbeanName);
        mbeanName = MBeans.register(prefix, name, this);
    }

    private void updateInfoCache() {
        LOG.debug("Updating info cache...");
        infoCache = infoBuilder.reset(lastRecs).get();
        LOG.debug("Done");
    }

    private int updateAttrCache() {
        LOG.debug("Updating attr cache...");
        int recNo = 0;
        int numMetrics = 0;
        for (MetricsRecordImpl record : lastRecs) {
            for (MetricsTag t : record.tags()) {
                setAttrCacheTag(t, recNo);
                ++numMetrics;
            }
            for (Metric m : record.metrics()) {
                setAttrCacheMetric(m, recNo);
                ++numMetrics;
            }
            ++recNo;
        }
        LOG.debug("Done. numMetrics=" + numMetrics);
        return numMetrics;
    }

    private static String tagName(String name, int recNo) {
        StringBuilder sb = new StringBuilder(name.length() + 16);
        sb.append("tag.").append(name);
        if (recNo > 0) {
            sb.append('.').append(recNo);
        }
        return sb.toString();
    }

    private void setAttrCacheTag(MetricsTag tag, int recNo) {
        String key = tagName(tag.name(), recNo);
        attrCache.put(key, new Attribute(key, tag.value()));
    }

    private static String metricName(String name, int recNo) {
        if (recNo == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length() + 12);
        sb.append(name);
        if (recNo > 0) {
            sb.append('.').append(recNo);
        }
        return sb.toString();
    }

    private void setAttrCacheMetric(Metric metric, int recNo) {
        String key = metricName(metric.name(), recNo);
        attrCache.put(key, new Attribute(key, metric.value()));
    }

    String name() {
        return name;
    }

    MetricsSource source() {
        return source;
    }

}
