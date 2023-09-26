#!/usr/bin/env groovy
import groovy.text.StreamingTemplateEngine
import groovy.xml.XmlSlurper

def method(Closure c){
    def firstValue = 'a'
    def secondValue = 'b'
    if (c.maximumNumberOfParameters == 1)
        c(firstValue)
    else
        c(firstValue, secondValue)
}

def method2(String a , Closure before, Closure after){
    println a
    before()
    after('after')
}

def method3(data){
    println data.args.name
    data.before()
    data.after('after')
}
//execute
def data = [
    args: [name: "hello"],
    before:{ a -> println " Run def $a"},
    after:{ a -> println " Run def $a"}
]
def bf = {a-> println "Run def $a"}
def af = {a-> println "Run def $a"}



def service = [
    "backendService" : "presit-web-backend:9000",
    "backendWsService": "presit-web-back:8095"
]

//service.each({ k, v ->
//    println "args.service >>> ${k} : ${v}"
//})




//method3(data);
def text = "sdfadfasf \$\\http: fasdf"
String resutl = new StreamingTemplateEngine().createTemplate(text).make(new HashMap()).toString()
println "result : ${resutl}"

//def text = '''<project xmlns="http://maven.apache.org/POM/4.0.0"
//         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
//           <version>1.2.3</version>
//         </project>
//         '''
//def root = new XmlParser().parse(new File("G:/projects/libs/pom.xml"))
//String version = root.version.text()
//
//def xml = new XmlSlurper().parse(new File("G:/projects/libs/pom.xml"))
//def doc = new XmlParser().parseText(text)
//println "result: ${doc}"
//
//println "mvn pom.xml project text: ${doc.project.text()}"
//println "mvn pom.xml project text: ${doc.project.version.text()}"

//method2("hello!!", af, bf)
//
//method2("hello!!") { a ->
//    println "Run $a"
//}
//{ b->
//     println "Run $b"
//}
//
//
//method { a ->
//    println "I just need $a"
//}
//method { a, b ->
//    println "I need both $a and $b"
//}