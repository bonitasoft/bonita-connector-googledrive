import groovy.xml.MarkupBuilder

// GroupIds that are already on the Bonita runtime classpath — exclude from jarDependencies
def bonitaRuntime = ['org.bonitasoft.engine', 'org.projectlombok', 'org.slf4j'] as Set

def xml = new StringWriter()
def builder = new MarkupBuilder(xml)
builder.jarDependencies {
    jarDependency("${project.artifactId}-${project.version}.${project.packaging}")
    project.artifacts
            .findAll { artifact ->
                (artifact.scope == "compile" || artifact.scope == "runtime" || artifact.scope == "provided") \
                && !bonitaRuntime.contains(artifact.groupId)
            }
            .sort { artifact -> artifact.artifactId }
            .each { artifact ->
                jarDependency("${artifact.artifactId}-${artifact.version}.${artifact.type}")
            }
}
def deps = xml.toString()
project.properties.setProperty("connector-dependencies", deps)
