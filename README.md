<h1 align="center">PLC2Skill - Automatically Generate Skills from PLC Code</h1>
<p align="center">
    <img width="400px" src="https://github.com/aljoshakoecher/PLC2Skill/blob/documentation/images/images/PLC2Skill-Logo.png">
</p>

Machine skills are executable functions of machines. Semantic models using ontologies are used to describe skills in a machine-readable way so that they can be registered in and executed from superordinate systems (e.g. Manufacturing Execution Systems). While semantic skill models provide a number of benefits (e.g. querying, reasoning, constraint-checking), they also have a huge drawback: Such models are complex and creating them manually is tedious and error-prone. In addition to that, PLC programmers who plan to implement a certain machine behavior and want to publish it as a skill, most often do not have any expertise in semantic technologies. PLC2Skill is a solution for this problem.

## How it works
<div align="center">
    <img width="600px" align="center" src="https://github.com/aljoshakoecher/PLC2Skill/blob/documentation/images/images/PLC2Skill_Workflow.png">
</div>
PLC2Skill is a method to automatically create skill models without any manual modelling efforts that consists of the following steps:

1. A PLC programmer uses the PLC2Skill library when programming a machine. This library helps in correctly programming skills. 
2. After programming a PLC using the PLC2Skill library, the program has to be exported to PLCopen XML. PLCopen XML is an open exchange format defined in IEC 61131-10. Using this format instead of an API of a PLC programming environment ensures vendor neutrality of PLC2Skill.
3. The imported PLC open XML file can be mapped into the skill model that is described [here](https://github.com/aljoshakoecher/Machine-Skill-Model).
4. With this model, it is possible to register skills at a skill based control platform that also follows the skill model

## Demo-Video
In case you want to see PLC2Skill in action, checkout this video of a little demonstration in a lab environment.
<div align="center">
    <a href="https://youtu.be/24dwANeT4Cs">
        <img width="600px" align="center" src="https://github.com/aljoshakoecher/plc2skill/blob/documentation/images/images/PLC2Skill_DemoVideo-Screenshot.png?raw=true">
    </a>
</div>

## How to use the PLC2Skill Library
The PLC2Skill library has to be imported into a PLC program. Skills can then be created by programming a function block (FB) that extends the abstract *skill* FB. Optional input parameters and output variables can be added by using the library's struct *SkillVariable*. The abstract *Skill* FB has an internal state machine (the one defined in ISA 88), that ensures that transitions can only be fired in the "correct" state. In every state, this state machine calls methods having the same name. For example, when switching to the "execute" state, the method execute() is called. These methods of the skill FB are empty because what is going to happen inside these methods is application-specific. In order for application-specific code to be called, these methods have to be overridden inside a concrete skill implementation that extends the library's skill FB.


## How to use the PLC2Skill Mapper
There are three different ways to use the PLC2Skill Mapper. You can either use it locally as a command line application, run it as a web-service or integrate it into your application as a library.

### CLI
Download the current `cli-x.x.x-jar-with-dependencies.jar` from the releases into a folder of your choice. Inside that folder, open a shell and execute `java -jar cli-x.x.x-jar -f <PLCopen XML file you want to map> -e <endpointURL of your PLC> -n <root component of the PLC project>`. The mapping result will be written to a file right next to the cli.jar.<br>
A Short description of the CLI arguments:
- -f / --file: The path to the PLCopen XML file that needs to be exported in the previous step
- -e / --endpointUrl: The endpointUrl of the OPC UA server running on the PLC. This is typically something like "opc.tcp://" + IP address of your PLC + ":" + Port of the UA server (default is 4840). So for UA server running on localhost with the default port, this argument would have to be set to "opc.tcp://localhost:4840".
- n / --nodeIdRoot: This one is a bit more tricky than the two before. Unfortunately, PLC open XML does not preserve all information about the PLC project. Information of the root parts of the projects are not exported. This information is often used to create node IDs for UA variables. These node IDs are needed in order to execute a skill via OPC UA. So in order for the PLC2Skill Mapper to create all node IDs, the missing root part of all node IDs has to be presented. You can determine this root component by comparing your PLC project in your programming environment to the root element in the exported PLC open XML file. A more detailed guide on how to determine this root component will follow soon...

### REST-API
Download the current `rest-api-x.x.x-jar-with-dependencies.jar` from the releases into a folder of your choice and from a shell, run `java -jar rest-api-x.x.x-jar-with-dependencies.jar`. This will start a web server and you can send HTTP POST request to `localhost:9191` to invoke the mapper. When creating the request, make sure to set the `Content-Type` header to `multipart/form-data`. Furthermore, the following information have to be sent inside the request body:
-  Key: "plc-file" - Value: The PLCopen XML file that should be mapped
-  Key: "endpointUrl" - Value: The endpoint URL of the PLC's OPC UA server (see above) as a string.
-  Key: "nodeIdRoot" - Value: The root component of all node IDs (see above) as a string.

The REST-API can be tested with Tools such as Postman and used e.g. by web applications. Our skill based control system interacts with the PLC2Skill Mapper via the REST API.

### Library
You can also include the mapping library which is used in both the CLI-application and REST API in your own projects. In order to do so, import `Plc2SkillMapper` and after obtaining a new instance of the mapper, use `executeMapping(<endpointUr>,<nodeIdRoot>, <Path to your MTP file>)`. A more detailed instruction on how to use the library will follow soon.

## How to cite
We are excited about everyone using PLC2Skill in their own applications. If you use PLC2Skill in research, please consider giving credit by citing the following paper:

```
@inproceedings{KJF_AMethodtoAutomatically_2021,
 author = {Köcher, Aljosha and Jeleniewski, Tom and Fay, Alexander},
 title = {{A Method to Automatically Generate Semantic Skill Models from PLC Code}},
 booktitle = {{IECON 2021 The 47th Annual Conference of the IEEE Industrial Electronics Society}},
 year = {2021},
}
```
