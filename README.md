# netcore-jenkins

## Daily Release Infra
![Tizen Daily Release Infra Structure](https://github.com/jyoungyun/netcore-jenkins/blob/master/Documentation/images/Tizen_Daily_Release_Infra.png)

### [root-generator](http://52.79.132.74:8080/job/root-generator/)
This is a seed job using [jobs/generation/RootGenerator.groovy](https://github.com/jyoungyun/netcore-jenkins/blob/master/jobs/generation/RootGenerator.groovy) file.

Required parameter:
* NUGET_FEED - https://tizen.myget.org/F/dotnet-core/api/v3/index.json
* NUGET_SFEED - https://tizen.myget.org/F/dotnet-core/symbols/api/v2/package
* NUGET_API_KEY - [API KEY](https://tizen.myget.org/feed/Details/dotnet-core)
* EMAIL - dotnet at samsung.com

Generated the following items:
* generator » poll
* release » core-setup_master_armel_Release
* release » core-setup_release_2.0.0_armel_Release
* release » coreclr_master_armel_Release
* release » coreclr_release_2.0.0_armel_Release
* release » corefx_master_armel_Release
* release » corefx_release_2.0.0_armel_Release
 
### [generator » poll](http://52.79.132.74:8080/job/generator/job/poll/)
1. Clone the 'dotnet/versions' repository every 10 minutes
2. Compare the saved version and latest version
   * Monitoring files
      * [coreclr - master](https://github.com/dotnet/versions/blob/master/build-info/dotnet/coreclr/master/Latest.txt)
      * [coreclr - release/2.0.0](https://github.com/dotnet/versions/blob/master/build-info/dotnet/coreclr/release/2.0.0/Latest.txt)
      * [corefx - master](https://github.com/dotnet/versions/blob/master/build-info/dotnet/corefx/master/Latest.txt)
      * [corefx - release/2.0.0](https://github.com/dotnet/versions/blob/master/build-info/dotnet/corefx/release/2.0.0/Latest.txt)
      * [core-setup - master](https://github.com/dotnet/versions/blob/master/build-info/dotnet/core-setup/master/Latest.txt)
      * [core-setup - release/2.0.0](https://github.com/dotnet/versions/blob/master/build-info/dotnet/core-setup/release/2.0.0/Latest.txt)

*If version updated*

3. Download that version of package from [dotnet-core myget](https://dotnet.myget.org/gallery/dotnet-core)
   * coreclr - Microsoft.NETCore.Runtime.CoreCLR
   * corefx  - Microsoft.Private.CoreFx.NETCoreApp
   * core-setup - Microsoft.NETCore.App
4. Get sha1 value from the package
5. Keep the latest version and sha1 value
6. Trigger each job

### [release » project_job](http://52.79.132.74:8080/job/release/)
1. Clone project git repository
2. Clone [netcore-jenkins](https://github.com/jyoungyun/netcore-jenkins.git) git repository
3. Build project code
4. Upload result packages to NUGET_FEED
5. Archive artifacts

## Official Release Infra
### [official-generator](http://52.79.132.74:8080/job/official-generator/)
This is a seed job using [jobs/generation/ReleaseGenerator.groovy](https://github.com/jyoungyun/netcore-jenkins/blob/master/jobs/generation/ReleaseGenerator.groovy) file.

Required parameter:
* NUGET_FEED - https://tizen.myget.org/F/dotnet-core/api/v3/index.json
* NUGET_SFEED - https://tizen.myget.org/F/dotnet-core/symbols/api/v2/package
* NUGET_API_KEY - [API KEY](https://tizen.myget.org/feed/Details/dotnet-core)
* EMAIL - dotnet@samsung.com

Generated the following items:
* generator » official_poll
* official-release » core-setup_armel
* official-release » coreclr_armel
* official-release » corefx_armel

### [generator » official_poll](http://52.79.132.74:8080/job/generator/job/official_poll/)
Required parameter:
* coreclr_version
* coreclr_minor_version
* corefx_version
* corefx_minor_version
* core_setup_version
* core_setup_minor_version
* patch_version

The *project_version* is an official build version like 1.0.0 and 2.0.0. The *project_minor_version* is an optionary build version like preview2-25407-01. The above version information can be found at [nuget.org](https://www.nuget.org/) after Microsoft releases the official version. You can refer to the **Microsoft.NETCore.Runtime.CoreCLR** package for coreclr, the **Microsoft.NETCore.Platforms** package for corefx, and the **Microsoft.NETCore.App** package for core-setup. The *patch_version* is a version for managing patches that need to be reflected in relation to Tizen after Microsoft fixes the code for release. Such a version is managed by tag in Samsung github [coreclr](https://github.sec.samsung.net/dotnet/coreclr/tags) and [corefx](https://github.sec.samsung.net/dotnet/corefx/tags). The [jobs/scripts/generate_patch.sh](https://github.com/jyoungyun/netcore-jenkins/blob/master/jobs/scripts/generate_patch.sh) script can be used to create a patch file that needs to be reflected. If you put the generated file in the [patches](https://github.com/jyoungyun/netcore-jenkins/tree/master/patches) directory of current project and specify *patch_version* with the same name, it will build the code after applying the patch at the time of build.

1. Download that version of package from [nuget.org](https://www.nuget.org/)
   * coreclr - Microsoft.NETCore.Runtime.CoreCLR
   * corefx  - Microsoft.NETCore.Platforms
   * core-setup - Microsoft.NETCore.App
2. Get sha1 value from the package
3. Trigger each job

### [official-release » project_job](http://52.79.132.74:8080/job/official-release/)
1. Clone project git repository
2. Clone [netcore-jenkins](https://github.com/jyoungyun/netcore-jenkins.git) git repository
3. Apply patch if a patch to apply exists
4. Build project code
5. Upload result packages to NUGET_FEED
6. Archive artifacts

### The list of packages that require official release
For .NETCore App
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.App
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.DotNetHost
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.DotNetHostPolicy
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.DotNetHostResolver
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.DotNetAppHost

For CoreCLR Native
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.Jit
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.TestHost
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.ILAsm
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.ILDAsm
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.Native
* runtime.tizen.4.0.0-armel.Microsoft.NETCore.Runtime.CoreCLR
