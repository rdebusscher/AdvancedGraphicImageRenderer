AdvancedGraphicImageRenderer
============================

Advanced PrimeFaces Graphic Image renderer for dynamic content


The 1.0.1 brach is meant to work against version 3.5 of eclipselink.
This branchs is dervided from the 1.0.0 tag.
It contains the same fixes that were added to the master branch namely:
<ul>
  <li> web-fragment.xml causing deployment problems on weblogic is refactored to not declare the listener since the container picks it up autoamtically and the namespaces are upgarded from j2ee
  <li> The GraphicImageManager get a fix to a tmp/ file leakage where with each request a new empty tmp file would get created and the image would not be rendered (because the file would be empty)
</ul>


In addition, the 1.0.1 branch also fixes a bug in the renderer AdvancedGraphicImageRenderer, which with each page refresh would leak an empty image in the tmp folder because the RID would not be unique.

