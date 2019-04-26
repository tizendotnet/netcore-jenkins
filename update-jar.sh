#!/bin/bash

__Dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

groovyc -d classes ${__Dir}/jobs/generation/Utilities.groovy
jar cvf ${__Dir}/jobs.generation.classes.jar -C classes .
