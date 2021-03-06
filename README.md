dxa-model-service
===
SDL Digital Experience Accelerator Model Service


About
-----
The SDL Digital Experience Accelerator (DXA) is a reference implementation of SDL Web (version 8, 8.5, or Cloud) that we provide to help you more quickly create, design, and publish a website based on SDL Web.

DXA is available for both .NET and Java web applications. Its modular architecture consists of a framework and example web application, which includes all core SDL Web functionality as well as separate Modules for additional, optional functionality.

This repository contains the source code of the DXA Model Service: a separate microservice that can be deployed on an SDL Web CIS backend, version 8 or higher.

The DXA Model Service is introduced in DXA 2.0 to improve overall performance and to provide a more lightweight DXA framework in the web application.

The full DXA Model Service distribution is downloadable from the SDL AppStore as part of a DXA .NET or Java installation package:
- DXA .NET: https://appstore.sdl.com/web-content-management/app/sdl-digital-experience-accelerator-net/608
- DXA Java: https://appstore.sdl.com/web-content-management/app/sdl-digital-experience-accelerator-java/737


Build
-----

You need Maven 3.2+ to build the Model Service from source. Maven should be available in the system `PATH`. 
    
To build DXA Model Service run the following command for the parent `dxa-model-service` project:

    mvn install 
    
Note, if you intend to just use the Model Service and not to make changes, you do not need to build it.  

Support
---------------
At SDL we take your investment in Digital Experience very seriously, and will do our best to support you throughout this journey. 
If you encounter any issues with the Digital Experience Accelerator, please reach out to us via one of the following channels:

- Report issues directly in [this repository](https://github.com/sdl/dxa-model-service/issues)
- Ask questions 24/7 on the SDL Web Community at https://tridion.stackexchange.com
- Contact Technical Support through the SDL Support web portal at https://www.sdl.com/support


Documentation
-------------
Documentation can be found online in the SDL documentation portal:

 - DXA Model Service [Installation](http://docs.sdl.com/LiveContent/content/en-US/SDL%20DXA-v10/GUID-2CF89E5B-D84C-498F-A65A-920EFC26A5A4)
 - DXA Model Service [Configuration](http://docs.sdl.com/LiveContent/content/en-US/SDL%20DXA-v10/GUID-53CC0D55-BD37-4874-A2F9-52F5DA831E13)


Repositories
------------
The following repositories with source code are available:

 - https://github.com/sdl/dxa-content-management - CM-side framework (.NET Template Building Blocks)
 - https://github.com/sdl/dxa-html-design - Whitelabel HTML Design
 - https://github.com/sdl/dxa-model-service - Model Service (Java)
 - https://github.com/sdl/dxa-modules - Modules (.NET and Java)
 - https://github.com/sdl/dxa-web-application-dotnet - ASP.NET MVC web application (including framework)
 - https://github.com/sdl/dxa-web-application-java - Java Spring MVC web application (including framework)


Branches and Contributions
--------------------------
We are using the following branching strategy:

 - `master` - Represents the latest stable version. This may be a pre-release version (tagged as `DXA x.y Sprint z`). Updated each development Sprint (approximately bi-weekly).
 - `develop` - Represents the latest development version. Updated very frequently (typically nightly).
 - `release/x.y` - Represents the x.y Release. If hotfixes are applicable, they will be applied to the appropriate release branch so that the branch actually represents the initial release plus hotfixes.

All releases (including pre-releases and hotfix releases) are tagged. 

Note that development sources (on `develop` branch) have dependencies on SNAPSHOT versions of the DXA artifacts, which are available here: https://oss.sonatype.org/content/repositories/snapshots/com/sdl/dxa/

If you wish to submit a Pull Request, it should normally be submitted on the `develop` branch so that it can be incorporated in the upcoming release.

Fixes for severe/urgent issues (that qualify as hotfixes) should be submitted as Pull Requests on the appropriate release branch.

Always submit an issue for the problem, and indicate whether you think it qualifies as a hotfix. Pull Requests on release branches will only be accepted after agreement on the severity of the issue.
Furthermore, Pull Requests on release branches are expected to be extensively tested by the submitter.

Of course, it is also possible (and appreciated) to report an issue without associated Pull Requests.


License
-------
Copyright (c) 2014-2018 SDL Group.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and limitations under the License.
