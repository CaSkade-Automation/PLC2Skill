# Mapping-MTP-to-OWL
This repository contains an automated mapping approach to transform a Module Type Package into an ontological skill model. This skill model consists of several individual ontologies which are all based on industry standards. You can find the skill model [here](https://github.com/aljoshakoecher/Machine-Skill-Model)


## Usage:
There are three different ways to use the MTP-to-Skill mapping. You can either use it locally as a command line application, run it as a web-service or integrate it into your application as a library.

### CLI
Download the current `cli-x.x.x-jar-with-dependencies.jar` from the releases into a folder of your choice. Inside that folder, open a shell and execute `java -jar cli-x.x.x-jar -f <Name of the file you want to map>`. The mapping result will be written to a file right next to the cli.jar.

### REST-API
Download the current `rest-api-x.x.x-jar-with-dependencies.jar` from the releases into a folder of your choice and from a shell, run `java -jar rest-api-x.x.x-jar-with-dependencies.jar`. This will start a web server and you can send HTTP POST request to `localhost:9191` to invoke the mapper. When creating the request, make sure to set the `Content-Type` header to `multipart/form-data`. Furthermore, you have to send the file with form key / name "mtp-file".

### Library
You can also include the library which is used in both the CLI-application and REST API in your own projects. In order to do so, import `MtpToSkillMapper` and after obtaining a new instance of the mapper, use `executeMapping(<Path to your MTP file>)`.