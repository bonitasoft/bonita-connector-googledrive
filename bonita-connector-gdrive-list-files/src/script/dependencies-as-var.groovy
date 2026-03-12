import org.apache.maven.project.MavenProject

MavenProject mavenProject = project

def sb = new StringBuilder()
sb.append('<jarDependencies>\n')

// Include the module's own JAR first
sb.append("        <jarDependency>${mavenProject.build.finalName}.jar</jarDependency>\n")

// Then all compile/runtime dependencies
mavenProject.artifacts.findAll { artifact ->
    artifact.scope in ['compile', 'runtime']
}.each { artifact ->
    sb.append("        <jarDependency>${artifact.file.name}</jarDependency>\n")
}
sb.append('    </jarDependencies>')

mavenProject.properties['connector-dependencies'] = sb.toString()
log.info("Generated connector-dependencies with ${mavenProject.artifacts.findAll { it.scope in ['compile', 'runtime'] }.size() + 1} JARs")
