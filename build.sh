rm -r build/jbittorrent
rm -r build/test
mkdir build
javac -sourcepath src -classpath build;ext/log4j-1.2.17.jar src/jbittorrent/*.java src/test/*.java -d build