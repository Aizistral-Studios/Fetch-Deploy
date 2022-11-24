# Fetch Deploy
Fetch Deploy is a simple solution for deploying static websites via GitHub. Here's a step-by-step instruction for how to use it:

## 1. Install Java on your target machine
While this project is built against Java 8, in principle it should be compatible with any of the more modern java versions. For instance, you can install OpenJDK:
```
sudo apt-get install openjdk-8-jre
```

## 2. Create GitHub repository with your website
Should be easy enough. If you use static website generator - your repository should have everything that is necessary to build a website. Pre-built websites that don't use generators are currently not supported.

## 3. Add action workflow to build the website
This will be specific to particular generator you use. For [aizistral.com](https://aizistral.com) I use [lemonarc/jekyll-action](https://github.com/marketplace/actions/jekyll-action), which simply builds a website without pushing it's contents anywhere by itself: [aizistral.com/.github/workflows/build.yml](https://github.com/Aizistral-Studios/aizistral.com/blob/master/.github/workflows/build.yml)

You will also have to add [upload-artifacts](https://github.com/marketplace/actions/upload-a-build-artifact) action to your workflow in order to collect built website and upload it as workflow artifact, see link above for an example of how I did that with Jekyll.

## 4. Configure Fetch Deploy
Now you need to place Fetch Deploy's `all` jar on the target machine, somewhere not too far from planned deployment path. Run it via terminal:

```
java -jar FetchDeploy-*-all.jar
```

Obviously you'll have to do it as user with appropriate permissions for directory where jar is located and planned deployment directory. On first run execution will terminate saying something along the lines of:

```
Organization is not specified in config
```

This is normal. In the same directory as the jar you should now find `config` folder with `config.json` file inside of it, which has a few fields you'll have to fill in, as well as few optional ones. Optional fields can be left empty:

- ### `organization` (mandatory)
Name of Github account or organization which owns the repository where your website is located.

- ### `repository` (mandatory)
Name of the repository with your website.

- ### `branch` (mandatory)
Repository branch from which your website should be deployed. By default this is set to `{DEFAULT}`; if left that way Fetch Deploy will automatically pick current default branch.

- ### `artifact` (mandatory)
Name of the artifact archive uploaded by your build workflow.

- ### `accessToken` (mandatory)
Access token with read permission for Github Actions. You can use either classic or fine-grained token. For classic token none of the permission scopes actually have to be selected when creating it. For fine-grained token you need at least read-only access granted in Repository permissions -> Actions.

If your repository is private then your access token obviously needs to grant general read access to that repository too.

- ### `deployPath` (mandatory)
Path to which contents of your website should be deployed, relative to working directory of the jar. Note that by default all contents of that directory will be deleted on each deployment.

- ### `deploymentDeletionExclusions` (optional)
Contains list of files and/or directories that should be excluded from deletion on deployment. This is useful if you have persistent files there that do not come with your website. By default it contains `.htaccess`.

- ### `errorDocsDeploymentPath` (optional)
Deployment path for error documents, in case you plan to deploy these to a different location than the rest of the website's contents. Both this and `errorDocsArchivePath` need to be specified in order for that to work.

- ### `errorDocsArchivePath` (optional)
Directory or path inside artifact archive where error documents are located.

- ### `cycleDelayMillis` (mandatory)
Delay between API requests to GitHub that check presence of new commits/artifacts, in milliseconds. By default this is set to 10000 (10 seconds).

- ### `debugLog` (mandatory)
Whether or not logging of some debug information should be enabled. By default this is set to false.

## 5. Run!
Once you filled in all the necessary fields, run the jar again and observe it deploy your website. You can use `screen` or something similar to keep console log accessible beyond the bounds of particular SSH session. Note that Fetch Deploy will create another file called `internal.json` in its config folder to keep track of which commits were already deployed; you can delete it and re-run the jar if you need to deploy the website from scratch.

## Extra features
You can use special placeholders in your website's layout, which will be automatically replaced by Fetch Deploy in all `.html` documents on deployment. Currently supported placeholders are:
- `{$brc}` - name of the branch from which the website was deployed;
- `{$brcl}` - full link to abovementioned branch on GitHub;
- `{$com}` - short name of commit from which the website was build, like `b49ac22`;
- `{$coml}` - full link to abovementioned commit on GitHub.
