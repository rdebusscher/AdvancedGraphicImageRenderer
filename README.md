AdvancedGraphicImageRenderer
============================

Advanced PrimeFaces Graphic Image renderer for dynamic content.

See [blog text](http://jsfcorner.blogspot.be/2012/11/advanced-primefaces-graphic-image.html) for more information.

Version | For PrimeFaces
-----------| -------------
1.0     | 3.4.1 / 3.5
1.1     | 4.0
1.2.1   | 5.x

Version 1.2 has a bug!

When you want to add the artifact, put following snippets in your `pom.xml`

    <repositories>
       <repository>
          <id>nexus_C4J</id>
          <url>http://nexus-osc4j.rhcloud.com/content/groups/public/</url>
       </repository>
    </repositories>

    <dependency>
        <groupId>be.rubus.web.jsf.primefaces</groupId>
        <artifactId>advanced-graphic-image</artifactId>
        <version>1.2.1</version>
    </dependency>
