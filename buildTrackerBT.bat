rmdir build\trackerBT
mkdir build
javac -sourcepath src -classpath build;ext\log4j-1.2.17.jar;ext\ant.jar;ext\freemarker.jar;ext\groovy.jar;ext\jaxen-core.jar;ext\jaxen-jdom.jar;ext\jdom.jar;ext\kxml.jar;ext\saxpath.jar;ext\simple-upload-0.3.4.jar;ext\velocity.jar;ext\xalan.jar;ext\xerces.jar;ext\xml-apis.jar src\trackerBT\*.java -d build