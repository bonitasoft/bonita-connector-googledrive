import org.apache.maven.project.MavenProject

MavenProject mavenProject = project

def sb = new StringBuilder()
sb.append('<jarDependencies>\n')
mavenProject.artifacts.findAll { artifact ->
    artifact.scope in ['compile', 'runtime']
}.each { artifact ->
    sb.append("        <jarDependency>${artifact.file.name}</jarDependency>\n")
}
sb.append('    </jarDependencies>')

mavenProject.properties['connector-dependencies'] = sb.toString()
log.info("Generated connector-dependencies with ${mavenProject.artifacts.findAll { it.scope in ['compile', 'runtime'] }.size()} JARs")
