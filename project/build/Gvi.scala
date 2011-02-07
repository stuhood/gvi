
import sbt._

class Gvi(info: ProjectInfo) extends DefaultProject(info)
{
    // for Avro's Netty dependency
    val jbossRepo = "JBoss Repo" at "https://repository.jboss.org/nexus/content/repositories/releases/"

    val avro = "org.apache.avro" % "avro" % "1.4.1" intransitive()

    val junitInterface = "com.novocode" % "junit-interface" % "0.5" % "test->default"

    // enable unchecked warnings
    override def compileOptions = CompileOption("-unchecked") :: super.compileOptions.toList
}
