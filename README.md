# Endeca Monitoring Plugin

## Overview

![images_community/download/attachments/122717981/icon.png](images_community/download/attachments/122717981/icon.png)

This monitor collects health and performance information from the Endeca dgraph server.

## Plugin Details

| Name | Endeca Monitoring Plugin
| :--- | :---
| Supported dynaTrace Versions | >= 5.5
| Author | Kevin Jackson (kevin.jackson@compuware.com)
| License | [dynaTrace BSD](dynaTraceBSD.txt)
| Support | [Not Supported ](https://community.compuwareapm.com/community/display/DL/Support+Levels#SupportLevels-Community)
| Release History | 2013-06-12 Initial Release
| Download | [EndecaMonitor_1.0.0.jar](com.dynatrace.diagnostics.plugin.EndecaMonitor_1.0.0.jar)  
|| [Endeca.dashboard.xml](Endeca.dashboard.xml)

## Provided Measures

  * CPU USAGE 

  * HOST REACHABLE 

  * NUMBER OF REQUESTS 

  * AVERAGE QUERY PERFORMANCE 

  * MAX QUERY PERFORMANCE 

  * AVERAGE REQUEST TIME 

  * MAX REQUEST TIME 

  * TIME SINCE LAST INDEX UPDATE 

## Configuration

This monitor uses a HTTP GET request to pull back measures from the Endeca dgraph server. The plugin is executed remotely from the dynaTrace collector(s).

NOTE: This plugin has been tested with dT versions 5.0 and 5.5, but should work with previous versions as well.  
NOTE: This plugin was tested against Endeca version 6.2.1.583084, but should also work with other versions, where the statistics on the dgraph server are the same.

