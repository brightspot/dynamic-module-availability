package brightspot.theme;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.psddev.cms.db.Content;
import com.psddev.cms.db.ToolUi;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.util.ClassFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Modification} of {@link CuratedStyleGroup} that adds a type field
 * for use in {@link brightspot.dynamicmoduleavailability.theme.StyleGroupEmbeddedContentCreator} to render
 * style groups only for these types.
 */
@ToolUi.FieldDisplayOrder({
        "displayName",
        "curatedStyleGroupTypes.types",
        "styles"

})
@Recordable.FieldInternalNamePrefix(CuratedStyleGroupModification.FIELD_PREFIX)
public class CuratedStyleGroupModification extends Modification<CuratedStyleGroup> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CuratedStyleGroupModification.class);

    static final String FIELD_PREFIX = "curatedStyleGroupTypes.";

    @Where("groups = ?/curatedStyleGroupTypes.getScopeGroups and isConcrete = true and deprecated != true and cms.ui.hidden != true and cms.ui.readOnly != true")
    private List<ObjectType> types;

    public List<ObjectType> getTypes() {
        if (types == null) {
            types = new ArrayList<>();
        }
        return types;
    }

    public void setTypes(List<ObjectType> types) {
        this.types = types;
    }

    @Ignored(false)
    @ToolUi.Hidden
    public List<String> getScopeGroups() {
        Set<Class<? extends CuratedStyleGroupTypesProvider>> concreteClasses = ClassFinder.findConcreteClasses(CuratedStyleGroupTypesProvider.class);

        // If no provider return default types.
        if (concreteClasses.isEmpty()) {
            return Collections.singletonList(Content.SEARCHABLE_GROUP);
        } else {

            List<String> types = new ArrayList<>();
            for (Class<? extends CuratedStyleGroupTypesProvider> providerClass : concreteClasses) {

                if (providerClass == null) {
                    continue;
                }

                try {
                    // Create instance of the provider from provider class
                    CuratedStyleGroupTypesProvider provider = providerClass.getDeclaredConstructor().newInstance();

                    // Add provider types
                    types.addAll(provider.getCuratedStyleGroupTypes());

                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    LOGGER.warn(
                            "Failed to create new instance of CuratedStyleGroupTypesProvider class: {}", providerClass.getName());
                }
            }

            return types;
        }
    }

}
