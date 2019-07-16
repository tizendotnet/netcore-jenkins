
package jobs.generation

class Utilities {
  private static String DefaultBranchOrCommitPR = '${sha1}'
  private static String DefaultBranchOrCommitPush = 'refs/heads/master'
  private static String DefaultRefSpec = '+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*'

  /**
   * Get the folder name for a job.
   *
   * @param project Project name (e.g. dotnet/coreclr)
   * @return Folder name for the project. Typically project name with / turned to _
   */
  def static getFolderName(String project) {
    return project.replace('/', '_')
  }

  /**
   * Get the standard job name of a job given the base job name, project, whether the
   * job is a PR or not, and an optional folder
   *
   * @param jobName Base name of the job
   * @param isPR True if PR job, false otherwise
   * @param folder (optional) If folder is specified, project is not used as the folder name
   * @return Full job name.  If folder prefix is specified,
   */
  def static getFullJobName(String jobName, boolean isPR, String folder = '') {
      return getFullJobName('', jobName, isPR, folder);
  }

  /**
   * Get the standard job name of a job given the base job name, project, whether the
   * job is a PR or not, and an optional folder
   *
   * @param project Project name (e.g. dotnet/coreclr)
   * @param jobName Base name of the job
   * @param isPR True if PR job, false otherwise
   * @param folder (optional) If folder is specified, project is not used as the folder name
   *
   * @return Full job name.  If folder prefix is specified,
   */
  def static getFullJobName(String project, String jobName, boolean isPR, String folder = '') {
    def jobSuffix = ''
    if (isPR) {
        jobSuffix = '_prtest'
    }

    def folderPrefix = ''
    if (folder != '') {
        folderPrefix = "${folder}/"
    }

    def fullJobName = ''
    if (jobName == '') {
        fullJobName = "${folderPrefix}innerloop${jobSuffix}"
    }
    else {
        fullJobName = "${folderPrefix}${jobName}${jobSuffix}"
    }

    return fullJobName
  }

  /**
   * Adds the standard parameters for PR and Push jobs.
   *
   * @param job Job to set up.
   * @param project Name of project
   * @param isPR True if job is PR job, false otherwise.
   * @param defaultBranchOrCommit Commit / branch to build.
   * @param defaultRefSpec the refs that Jenkins must sync on a PR job
   */
  def static addStandardParametersEx(def job, String project, boolean isPR, String defaultBranchOrCommit, String defaultRefSpec) {
    // Do not replace */ if there is another wildcard in the branch name (like */*)
    if (defaultBranchOrCommit.indexOf('*/') == 0 && defaultBranchOrCommit.lastIndexOf('*') == 0){
      defaultBranchOrCommit = defaultBranchOrCommit.replace('*/','refs/heads/')
    }

    if (isPR) {
      addStandardPRParameters(job, project, defaultBranchOrCommit, defaultRefSpec)
    }
    else {
      addStandardNonPRParameters(job, project, defaultBranchOrCommit)
    }
  }

  /**
   * Calculates the github scm URL give a project name
   *
   * @param project Github project (org/repo)
   */
  def static calculateGitHubURL(def project) {
    // Example: git://github.com/dotnet/corefx.git
    return "https://github.com/${project}"
  }

  /**
   * Adds parameters to a non-PR job.  Right now this is only the git branch or commit.
   * This is added so that downstream jobs get the same hash as the root job
   */
  def private static addStandardNonPRParameters(def job, String project, String defaultBranch) {
    job.with {
      parameters {
        stringParam('GitBranchOrCommit', defaultBranch, 'Git branch or commit to build.  If a branch, builds the HEAD of that branch.  If a commit, then checks out that specific commit.')
      }
    }
  }

  /**
   * Adds the private job/PR parameters to a job.  Note that currently this shouldn't used on a non-pr job because
   * push triggering may not work.
   */
  def static addStandardPRParameters(def job, String project, String defaultBranchOrCommit = null, String defaultRefSpec = null) {
    defaultBranchOrCommit = getDefaultBranchOrCommitPR(defaultBranchOrCommit)
    defaultRefSpec = getDefaultRefSpec(defaultRefSpec)

    job.with {
      parameters {
        stringParam('GitBranchOrCommit', defaultBranchOrCommit, 'Git branch or commit to build.  If a branch, builds the HEAD of that branch.  If a commit, then checks out that specific commit.')
        stringParam('GitRepoUrl', calculateGitHubURL(project), 'Git repo to clone.')
        stringParam('GitRefSpec', defaultRefSpec, 'RefSpec.  WHEN SUBMITTING PRIVATE JOB FROM YOUR OWN REPO, CLEAR THIS FIELD (or it won\'t find your code)')
      }
    }
  }

  def public static addScm(def job, String project, boolean isPR) {
    if (isPR) {
      addPRTestSCM(job, project,  null)
    }
    else {
      addNonPRScm(job, project, null)
    }
  }

  /**
   * Adds private job/PR test SCM.  This is slightly different than normal
   * SCM since we use the parameterized fields for the refspec, repo, and branch
   */
  def private static addPRTestSCM(def job, String project, String subdir) {
    job.with {
      scm {
        git {
          remote {
            github(project)

            // Set the refspec
            refspec('${GitRefSpec}')

            // Reset the url to the parameterized version
            url('${GitRepoUrl}')
          }

          branch('${GitBranchOrCommit}')

          // Raise up the timeout
          extensions {
            if (subdir != null) {
              relativeTargetDirectory(subdir)
            }
            cloneOptions {
              timeout(90)
            }
          }
        }
      }
    }
  }

  def private static addNonPRScm(def job, String project, String subdir) {
    job.with {
      scm {
        git {
          remote {
            github(project)
          }

          branch('${GitBranchOrCommit}')

          // Raise up the timeout
          extensions {
            if (subdir != null) {
              relativeTargetDirectory(subdir)
            }
            cloneOptions {
              timeout(90)
            }
          }
        }
      }
    }
  }

  /**
   * Performs standard job setup for a newly created job.
   * Includes: Basic parameters, Git SCM, and standard options
   *
   * @param job Job to set up.
   * @param project Name of project
   * @param isPR True if job is PR job, false otherwise.
   * @param defaultBranchOrCommit Commit / branch to build.
   * @param defaultRefSpec the refs that Jenkins must sync on a PR job
   */
  def private static standardJobSetupEx(def job, String project, boolean isPR, String defaultBranchOrCommit, String defaultRefSpec) {
    Utilities.addStandardParametersEx(job, project, isPR, defaultBranchOrCommit, defaultRefSpec)
    Utilities.addScm(job, project, isPR)
    Utilities.addStandardOptions(job, isPR)
  }

  /**
   * Performs standard job setup for a newly created job.
   * Includes: Basic parameters, Git SCM, and standard options
   *
   * @param job Job to set up.
   * @param project Name of project
   * @param isPR True if job is PR job, false otherwise.
   * @param branch If not a PR job, the branch that we should be building.
   */
  def static standardJobSetup(def job, String project, boolean isPR, String branch) {
    String defaultRefSpec = getDefaultRefSpec(null)
    if (isPR) {
      branch = getDefaultBranchOrCommitPR(null)
    }
    standardJobSetupEx(job, project, isPR, branch, defaultRefSpec)
  }

  def static String getDefaultBranchOrCommitPR(String defaultBranchOrCommit) {
    return getDefaultBranchOrCommit(true, defaultBranchOrCommit);
  }

  def static String getDefaultBranchOrCommit(boolean isPR, String defaultBranchOrCommit) {
    if (defaultBranchOrCommit != null) {
      return defaultBranchOrCommit;
    }

    if (isPR) {
      return DefaultBranchOrCommitPR;
    }
    else {
      return DefaultBranchOrCommitPush;
    }
  }

  def static String getDefaultRefSpec(String refSpec) {
    if (refSpec != null) {
      return refSpec;
    }

    return DefaultRefSpec;
  }

  /**
   * Add standard options to a job.
   *
   * @param job Input job
   * @param isPR True if the job is a pull request job, false otherwise.
   */
  def static addStandardOptions(def job, def isPR = false) {
    job.with {
      // Enable concurrent builds
      concurrentBuild()

      // 5 second quiet period before the job can be scheduled
      quietPeriod(5)

      wrappers {
        timestamps()
        // Add a pre-build wipe-out
        preBuildCleanup()
      }

      // Add a post-build cleanup.  Order that this post-build step doesn't matter.
      // It runs after everything.
      publishers {
        wsCleanup {
          cleanWhenFailure(true)
          cleanWhenAborted(true)
          cleanWhenUnstable(true)
          includePattern('**/*')
          deleteDirectories(true)
        }
      }
    }

    Utilities.setJobTimeout(job, 120)
    Utilities.addRetentionPolicy(job, isPR)
    // Add a webhook to gather job events for Jenkins monitoring.
    // The event hook is the id of the event hook URL in the Jenkins store
    // Remove build event webhooks to remote source of test result API calls.
    // Utilities.setBuildEventWebHooks(job, ['helix-int-notification-url', 'helix-prod-notification-url'])
  }

  /**
   * Get the project name for a job.
   *
   * @param project Project name (e.g. coreclr)
   * @param branch Branch name (e.g. master)
   * @param targetArch Target architecture name (e.g. armel)
   * @param config Build configuration (e.g. release)
   * @return Project name for a job.
   */
  def static getProjectName(String project, String branch, String targetArch, String config) {
    return getFolderName("${project}_${branch}_${targetArch}_${config}")
  }

  /**
   * Get docker image.
   *
   * @param branch Branch name (e.g. master)
   * @return Docker image name.
   */
  def public static String getDockerImage(String branch) {
    switch (branch) {
      case 'release/2.0.0':
        return 'hqueue/dotnetcore:ubuntu1404_cross_prereqs_v4-tizen_rootfs'
      case 'release/2.1':
      case 'release/2.2':
        return 'tizendotnet/dotnet-buildtools-prereqs:ubuntu-16.04-cross-e435274-20180426002255-tizen-rootfs-5.0m1'
      case 'release/3.0':
      case 'master':
        return 'tizendotnet/dotnet-buildtools-prereqs:ubuntu-16.04-cross-10fcdcf-20190208200917-tizen-rootfs-5.0m2'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  /**
   * Get Tizen version.
   *
   * @param branch Branch name (e.g. master)
   * @return Tizen version.
   */
  def public static String getTizenVersion(String branch) {
    switch (branch) {
      case 'release/2.0.0':
        return 'tizen.4.0.0'
      case 'release/2.1':
      case 'release/2.2':
      case 'release/3.0':
      case 'master':
        return 'tizen.5.0.0'
      default:
        assert false : "Unknown branch: '${branch}'"
        break
    }
  }

  /**
   * Get the docker command.
   *
   * @param projectDir (optional) Project directory to work
   * @return Docker command
   */
  def static getDockerCommand(String branch, String projectDir = "") {
    def workingDirectory = "/opt/code"
    def environment = "-e ROOTFS_DIR=/crossrootfs/\${targetArch}.tizen.build"
    def command = "docker run --rm -v \${WORKSPACE}${projectDir}:${workingDirectory} -w=${workingDirectory} ${environment} ${getDockerImage(branch)}"
    return command
  }

  /**
   * Add a build steps.
   *
   * @param job Job to add build steps
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addBuildSteps(def job, String project, String branch, String projectDir = "") {
    if (projectDir != "") {
        projectDir = "/" + projectDir
    }

    def dockerCommand = getDockerCommand(branch, projectDir)

    job.with {
      steps {

        // see https://github.com/dotnet/corefx/pull/30825 for details of versioning

        conditionalSteps {
          condition {
            and {
              not {
                stringsMatch("\${version}", "stable", false)
              }
            }
            {
              not {
                stringsMatch("\${version}", "servicing", false)
              }
            }
            {
              not {
                stringsMatch("\${version}", "rtm", false)
              }
            }
          }

          steps {
            environmentVariables {
              env("buildIdOpts", "/p:OfficialBuildId=\${buildid}")
            }
          }
        }

        conditionalSteps {
          condition {
            stringsMatch("\${version}", "stable", false)
          }

          steps {
            environmentVariables {
              env("stableOpts", "/p:StabilizePackageVersion=true")
              env("packageOpts", "/p:PackageVersionStamp=")
            }
          }
        }

        if (project == 'corefx') {
          conditionalSteps {
            condition {
              shell("echo \${version} | grep -q servicing")
            }

            steps {
              environmentVariables {
                env("stableOpts", "/p:StabilizePackageVersion=true")
                env("packageOpts", "/p:PackageVersionStamp=servicing")
              }
            }
          }

          conditionalSteps {
            condition {
              shell("echo \${version} | grep -q rtm")
            }

            steps {
              environmentVariables {
                env("stableOpts", "/p:StabilizePackageVersion=true")
                env("packageOpts", "/p:PackageVersionStamp=rtm")
              }
            }
          }
        } else {
          conditionalSteps {
            condition {
              or {
                stringsMatch("\${version}", "servicing", false)
              }
              {
                stringsMatch("\${version}", "rtm", false)
              }
            }

            steps {
              environmentVariables {
                env("stableOpts", "/p:StabilizePackageVersion=true")
                env("packageOpts", "/p:PackageVersionStamp=\${buildid}")
              }
           }
          }
        }

        def passCI = ''
        if (project == 'corefx' && (branch == 'master' || branch == 'release/3.0')) {
          // Master uses Arcade for build and requires --ci option to be passed
          passCI = '--ci'
        }
        else if (project == 'coreclr' && (branch == 'master' || branch == 'release/3.0')) {
          passCI = '/p:ContinuousIntegrationBuild=true'
        }

        def authorsOpts = '/p:Authors=Tizen'

        if (project == 'coreclr') {
          shell("${dockerCommand} ./build.sh cross \${config} \${targetArch} cmakeargs -DFEATURE_GDBJIT=TRUE cmakeargs -DFEATURE_PREJIT=TRUE cmakeargs -DFEATURE_NGEN_RELOCS_OPTIMIZATIONS=TRUE stripSymbols -PortableBuild=false -- \${buildIdOpts} \${stableOpts} \${packageOpts} ${authorsOpts} ${passCI}")
        } else if (project == 'corefx') {
          if (branch == 'master' || branch == 'release/3.0') {
            // Build command for CoreFX has changed: see https://github.com/dotnet/corefx/pull/32798/files and https://github.com/dotnet/corefx/commit/66392f577c7852092f668876822b6385bcafbd44

            shell("${dockerCommand} ./build.sh --configuration \${config} /p:ArchGroup=\${targetArch} /p:RuntimeOS=${getTizenVersion(branch)} /p:PortableBuild=false /p:EnableNgenOptimization=false ${passCI} \${buildIdOpts} \${stableOpts} \${packageOpts} /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
          } else {
            shell("${dockerCommand} ./build-managed.sh -\${config} -buildArch=\${targetArch} -RuntimeOS=${getTizenVersion(branch)} -PortableBuild=false -- \${buildIdOpts} \${stableOpts} \${packageOpts} /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
            shell("${dockerCommand} ./build-native.sh -\${config} -buildArch=\${targetArch} -RuntimeOS=${getTizenVersion(branch)} -PortableBuild=false -- \${buildIdOpts} \${stableOpts} \${packageOpts} /p:BinPlaceNETCoreAppPackage=true /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts}")
            shell("${dockerCommand} ./build-packages.sh -\${config} -ArchGroup=\${targetArch} -RuntimeOS=${getTizenVersion(branch)} -PortableBuild=false -- ${authorsOpts}")
          }
        } else if (project == 'core-setup') {
          shell("${dockerCommand} ./build.sh -ConfigurationGroup=\${config} -TargetArchitecture=\${targetArch} -SkipTests=true -DisableCrossgen=true -PortableBuild=false -CrossBuild=true -- \${buildIdOpts} \${stableOpts} \${packageOpts} /p:OverridePackageSource=https:%2F%2Ftizen.myget.org/F/dotnet-core/api/v3/index.json ${authorsOpts} /p:OutputRid=${getTizenVersion(branch)}-\${targetArch}")
        }
        // Change ownership to UID of the projectDir
        // Building with docker, it will be created as root with the file it downloaded
        // or generated during the build process. In this case, when jenkins remove workspace,
        // it can not access root permission files, so it can not be removed normally.
        // This issue causes the disk size to become insufficient.
        shell("${dockerCommand} chown \$( id -u \${USER} ):\$( id -u \${USER} ) . -R")
      }
    }
  }

  /**
   * Archives data for a job.
   *
   * @param job Job to modify
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addArchival(def job, String project, String branch, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    job.with {
      publishers {
        archiveArtifacts {
          if (project == 'coreclr') {
            pattern(projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget/pkg/*.nupkg')
            pattern(projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget/symbolpkg/*.nupkg')
          } else if (project == 'corefx') {
            if (branch == "master" || branch == "release/3.0") {
              // On latest master bin/ folder is moved to artifacts/
              pattern(projectDir + 'artifacts/packages/\${config}/*.nupkg')
            } else {
              pattern(projectDir + 'bin/packages/\${config}/*.nupkg')
            }
          } else if (project == 'core-setup') {
            if (branch == 'master' || branch == 'release/3.0') {
              pattern(projectDir + "bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.nupkg")
              pattern(projectDir + "bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.tar.gz")
            } else {
              pattern(projectDir + "Bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.nupkg")
              pattern(projectDir + "Bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages/*.tar.gz")
            }
          }
          onlyIfSuccessful()
        }
      }
    }
  }

  /**
   * Get the nuget command.
   *
   * @param nugetMap Nuget configuration settings
   * @param path Directory path for uploading packages
   * @return Nuget command
   */
  def static getNugetCommand(def nugetMap, String path) {
    return "netcore-jenkins/jobs/scripts/upload_nupkg.sh netcore-jenkins/dotnet-dev " + path + " " + nugetMap['feed'] + " " + nugetMap['sfeed'] + " " + nugetMap['key']
  }

  /**
   * Upload packages for a job.
   *
   * @param job Job to modify
   * @param nugetMap Nuget configuration settings
   * @param project Project name to build
   * @param projectDir (optional) Project directory to build
   */
  def static addUploadSteps(def job, def nugetMap, String project, String branch, String projectDir = "") {
    if (projectDir != "") {
        projectDir = projectDir + '/'
    }

    def nugetCommand = ""
    if (project == 'coreclr') {
      nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/Product/Linux.\${targetArch}.\${config}/.nuget')
    } else if (project == 'corefx') {
      if (branch == "master" || branch == "release/3.0") {
        // On latest master bin/ folder is moved to artifacts/
        nugetCommand = getNugetCommand(nugetMap, projectDir + 'artifacts/packages/\${config}')
      } else {
        nugetCommand = getNugetCommand(nugetMap, projectDir + 'bin/packages/\${config}')
      }
    } else if (project == 'core-setup') {
      if (branch == 'master' || branch == 'release/3.0') {
        nugetCommand = getNugetCommand(nugetMap, projectDir + "bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages")
      } else {
        nugetCommand = getNugetCommand(nugetMap, projectDir + "Bin/${getTizenVersion(branch)}-\${targetArch}.\${config}/packages")
      }
    }

    job.with {
      steps {
        shell("set +x && ${nugetCommand}")
      }
    }
  }

  /**
   * Set the job timeout to the specified value.
   *
   * @param job Input job to modify
   * @param jobTimeout Set the job timeout.
   */
  def static setJobTimeout(def job, int jobTimeout) {
    job.with {
      wrappers {
        timeout {
          absolute(jobTimeout)
        }
      }
    }
  }

  /**
   * Adds a retention policy for artifacts
   *
   * @param job Job to modify
   * @param isPR True if the job is a pull request job, false otherwise.  If isPR is true,
   *             a more restrictive retention policy is use.
   */
  def static addRetentionPolicy(def job, boolean isPR = false) {
    job.with {
      // Enable the log rotator
      logRotator {
        if (isPR) {
          artifactDaysToKeep(5)
          daysToKeep(7)
          artifactNumToKeep(25)
          numToKeep(150)
        }
        else {
          artifactDaysToKeep(5)
          daysToKeep(21)
          artifactNumToKeep(25)
          numToKeep(100)
        }
      }
    }
  }

 /**
  * Adds a github PR trigger for a job
  *
  * @param job Job to add the PR trigger for
  * @param contextString String to use as the context (appears in github as the name of the test being run).
  *                      If empty, the job name is used.
  * @param triggerPhraseString String to use to trigger the job.  If empty, the PR is triggered by default.
  * @param triggerOnPhraseOnly If true and trigger phrase string is non-empty, triggers only using the specified trigger
  *                            phrase.
  * @param permitAllSubmitters If true all PR submitters may run the job
  * @param permittedOrgs If permitAllSubmitters is false, at least permittedOrgs or permittedUsers should be non-empty.
  * @param permittedUsers If permitAllSubmitters is false, at least permittedOrgs or permittedUsers should be non-empty.
  * @param branchName If null, all branches are tested.  If not null, then is the target branch of this trigger
  */
def private static addGithubPRTriggerImpl(def job, String branchName, String contextString, String triggerPhraseString, boolean triggerOnPhraseOnly) {
    job.with {
      triggers {
        githubPullRequest {
          useGitHubHooks()

          permitAll()

          extensions {
            commitStatus {
              context(contextString)
              //updateQueuePosition(true)
            }
          }

          if (triggerOnPhraseOnly) {
            onlyTriggerPhrase(triggerOnPhraseOnly)
          }
          triggerPhrase(triggerPhraseString)

          if (branchName != null) {
            // We should only have a flat branch name, no wildcards
            assert branchName.indexOf('*') == -1
            whiteListTargetBranches([branchName])
          }
        }
      }
    }

    addJobRetry(job)
  }

  /**
   * Adds an auto-retry to a job
   */
  def private static addJobRetry(def job) {
    List<String> expressionsToRetry = [
      'channel is already closed',
      'Connection aborted',
      'Cannot delete workspace',
      'failed to mkdirs',
      'ERROR: Timeout after 10 minutes',
      'Slave went offline during the build',
      '\'type_traits\' file not found', // This is here for certain flavors of clang on Ubuntu, which can exhibit odd errors.
      '\'typeinfo\' file not found', // This is here for certain flavors of clang on Ubuntu, which can exhibit odd errors.
      'Only AMD64 and I386 are supported', // Appears to be a flaky CMAKE failure
      'java.util.concurrent.ExecutionException: Invalid object ID',
      'hexadecimal value.*is an invalid character.', // This is here until NuGet cache corruption issue is root caused and fixed.
      'The plugin hasn\'t been performed correctly: Problem on deletion',
      'No space left on device'
      ]
    def regex = '(?i).*('
    regex += expressionsToRetry.join('|')
    regex += ').*'

    def naginatorNode = new NodeBuilder().'com.chikli.hudson.plugin.naginator.NaginatorPublisher' {
      regexpForRerun(regex)
      rerunIfUnstable(false)
      rerunMatrixPart(false)
      checkRegexp(true)
      maxSchedule(3)
    }

    def delayNode = new NodeBuilder().delay(class: 'com.chikli.hudson.plugin.naginator.FixedDelay') {
      delegate.delay(15)
    }

    naginatorNode.append(delayNode)

    job.with {
      configure { proj ->
        def currentPublishers = proj / publishers
        currentPublishers << naginatorNode
      }
    }
  }

  /**
   * Adds a github PR trigger for a job that is specific to a particular branch
   *
   * @param job Job to add the PR trigger for
   * @param branchName If the target branch for the PR message matches this target branch, then the trigger is run.
   * @param contextString String to use as the context (appears in github as the name of the test being run).
   *                      If empty, the job name is used.
   * @param triggerPhraseString String to use to trigger the job.  If empty, the PR is triggered by default.
   * @param triggerOnPhraseOnly If true and trigger phrase string is non-empty, triggers only using the specified trigger
   *                            phrase.
   */
  def static addGithubPRTriggerForBranch(def job, String branchName, String contextString,
    String triggerPhraseString = '', boolean triggerOnPhraseOnly = true) {

    assert contextString != ''

    if (triggerPhraseString == '') {
      triggerOnPhraseOnly = false
      triggerPhraseString = "(?i).*test\\W+${java.util.regex.Pattern.quote(contextString)}.*"
    }

    Utilities.addGithubPRTriggerImpl(job, branchName, contextString, triggerPhraseString, triggerOnPhraseOnly)
  }

  def static addGithubPushTrigger(def job) {
    job.with {
      triggers {
          githubPush()
      }
    }

    Utilities.addJobRetry(job)
  }

  /**
   * Set email notifications for unstable builds.
   *
   * @param job Job to modify
   * @param recipients Recipients to receive
   */
  def static setEmailNotification(def job, String recipients) {
    job.with {
      publishers {
        mailer(recipients, false, false)
      }
    }
  }

  /**
   * Download patch.
   *
   * @param job Job to download patch
   * @param patch Patch to download
   * @param outdir (optional) Directory to save patch
   */
  def static getPatch(def job, String patch, String outdir = "") {
    job.with {
      steps {
        shell("wget https://raw.githubusercontent.com/tizendotnet/netcore-jenkins/master/patches/${patch} --directory-prefix \${WORKSPACE}/${outdir}")
      }
    }
  }

  /**
   * Apply patch.
   *
   * @param job Job to apply patch
   * @param patch Patch to apply
   * @param projectDir (optional) Project directory to build
   */
  def static applyPatch(def job, String patch, String projectDir = "") {
    job.with {
      steps {
        shell("if [ -f \${WORKSPACE}/${patch} ]; then git -C \${WORKSPACE}/${projectDir} apply \${WORKSPACE}/${patch}; fi")
      }
    }
  }

  /**
   * Change ownership to jenkins UID of the projectDir
   *
   * @param job Job to fix permissions
   * @param projectDir (optional) Project directory
   */
  def static fixPermissions(def job, String projectDir = ".") {
    job.with {
      steps {
        shell("sudo chown \$( id -u \${USER} ):\$( id -u \${USER} ) ${projectDir} -R")
      }
    }
  }
}
