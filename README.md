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


<p> Release Candaidate Notes:
This forked branch of the original 1.0.0 branch is meant to work agianst primefaces 3.5 and has the following fixes.

<ul>
<li>
(1) Synchronization that was missing on the resource manager - very simple synchroniztaiton
<li>(2) Buts the Resource Manager as serilizable
<li>(3) Reviews the process of generate unique ids - not even in the HEAD release is the code reselient to page refresh leaking images of 0 bytes due to the stream being closed.
<li>(4) Is reselient to browser cache by producing a new unique id in a pseudo deterministic way, meaning that when it makes sense to produce a new unique id a new unique id is produced and it will be an id that the browser has not yet sean safeguarding from cache issues
<li>(5) fixes leaking file input sream on the resource handler tha twas not closing the stream
<li>(6) is more light weith in terms of pyhsical space consumption in the temp folder as it attempts on the fly to delete the stale old image generated on a previous request when a new stream content is created for the renderer
<li>(7) It enhances the concept of when the Renderer actually decides to go ahead with the advanced graphic rendering algorithm or to ignore it and delegate to the default primefaces implementation.  (e.g if the user tried to force advancade rendering to take place by puting in the new advanced rendering tag under p:graphicImage, the advanced algorithm would always be triggered. This might not be desirable if the p:grpahic image has a non trivial value expression such as
#{empty someBean.imageStramedContent ? resource['image/myStaticResourceForNoImage.jpg'] : someBean.myStreamedContent} where the outcome of the VE can either be a static resource or a streamed content. In such a case we would prefer for primefaces defaul implementation to take over while the advanced graphic image is really meant to target only streamed content since we know the expression cannot be evaluated during the resource handler execution.

</ul>

