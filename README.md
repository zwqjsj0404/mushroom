# Mushroom [![Build Status](https://travis-ci.org/zavakid/mushroom.png?branch=master)](https://travis-ci.org/zavakid/mushroom)
## Introduction 
a metric framework extracted from Hadoop metric2 package

mushroom 是一个 metric for java 的方案，目前 v0.1 版本的源码均来自 Hadoop 1.0.4 的 metric2 package，你可以理解 v0.1 - v0.2 版本就是 fork hadoop 中的 metric 代码(v0.2只有少量改动)。未来的 v1.0以及之后的版本，进行相应的变更或者功能增加（具体需要看相应版本的 change log）

## Release Note
1. v0.2 released at 2013-04-11, [change log][v1 change log]


## Quick Start
if you use Maven, you can add the dependency in your POM
```xml
<dependency>
  <groupId>com.zavakid</groupId>
  <artifactId>mushroom</artifactId>
  <version>0.2</version>
</dependency>
```

also you can fork the [quick start example][quick start example repo]

## User Guide
check out the [user guide][user guide]



[user guide]: https://github.com/zavakid/mushroom/wiki/User-Guide
[v1 change log]: https://github.com/zavakid/mushroom/issues?milestone=2&state=closed
[quick start example repo]: https://github.com/zavakid/mushroom-example
