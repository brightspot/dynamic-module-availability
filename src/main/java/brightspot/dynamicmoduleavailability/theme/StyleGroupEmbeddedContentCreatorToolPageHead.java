package brightspot.dynamicmoduleavailability.theme;

import java.io.IOException;

import com.psddev.cms.tool.ToolPageContext;
import com.psddev.cms.tool.ToolPageHead;
import com.psddev.dari.util.Cdn;

/**
 * Provides script and style dependencies for {@link StyleGroupEmbeddedContentCreator} functionality.
 * Executed by {@link ToolPageContext#writeHeader()} to add
 * additional elements to the head of Tool pages.
 */
public class StyleGroupEmbeddedContentCreatorToolPageHead implements ToolPageHead {

    @Override
    public void writeHtml(ToolPageContext page) throws IOException {
        page.writeElement("link",
                "rel", "stylesheet",
                "type", "text/css",
                "href", Cdn.getUrl(page.getRequest(), "/_resource/brightspot/dynamicmoduleavailability/theme/StyleGroupEmbeddedContentCreator.css")
        );

        page.writeStart("script", "type", "text/javascript", "src", "/_resource/brightspot/dynamicmoduleavailability/theme/StyleGroupEmbeddedContentCreator.js").writeEnd();
    }
}
