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

import java.util.ArrayList;

/**
 * Builder for the immutable metrics buffers
 * 
 * @author Hadoop metric2 package's authors
 * @author zavakid 2013 2013-4-4 下午10:09:15
 * @since 0.1
 */
class MetricsBufferBuilder extends ArrayList<MetricsBuffer.Entry> {

    private static final long serialVersionUID = 1L;

    boolean add(String name, Iterable<MetricsRecordImpl> records) {
        return add(new MetricsBuffer.Entry(name, records));
    }

    MetricsBuffer get() {
        return new MetricsBuffer(this);
    }

}
