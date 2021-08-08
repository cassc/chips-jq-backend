clean:
	rm -rf ./gen-java; rm -rf ./gen-php

java:
	thrift --gen java shared.thrift

php: 
	thrift --gen php shared.thrift

all: clean java php

