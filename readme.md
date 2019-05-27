# SemanticEngine
A semantic engine for creating context-aware reactive applications

## Building
This is a simple Maven project. Run `mvn install` and two jars are produced in `target`.  
One already contains all the needed dependencies.

## Running and usage
Simply execute `java -jar semanticengine-<version>-jar-with-dependencies.jar`.
Once started you can interact with the engine using the REST API or by simply entering 
SPARQL queries from the command line.
You can initialize the content of the knowledge base by running the engine with the 
`-t` switch followed by the path of a Turtle file. The `-p` switch can be used to change 
the port of the API endpoint.

## The API
The engine exposes a simple REST API, the HTTP server listens at port 9876 (that can 
be changed with a commandline switch) and the 
resources are available under the "/engineapi" path.

Available resources and supported methods:
- `[/] [GET]` - returns all the triples in the triplestore in plain text
- `[/observations] [POST]` - expects a JSON describing an observation that is committed 
  to the triplestore. Properties of the JSON: `madeBySensor`; `observedProperty`; `hasFeatureOfInterest`; 
  `resultTime` (optional, in `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` format); 
- `[/selects] [/updates] [/constructs] [POST]` - all accept a plain text SPAQL query and run 
  it against the triplestore.
