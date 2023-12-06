package brightspot.dynamicmoduleavailability.theme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import brightspot.theme.CuratedStyleGroup;
import brightspot.theme.StyleGroupSettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.psddev.cms.db.Content;
import com.psddev.cms.db.Copyable;
import com.psddev.cms.db.Directory;
import com.psddev.cms.db.EmbeddedContentCreator;
import com.psddev.cms.db.Site;
import com.psddev.cms.db.SiteSettings;
import com.psddev.cms.db.ToolUserRecentlyUsed;
import com.psddev.cms.theme.CuratedStyleGroupModification;
import com.psddev.cms.tool.ToolPageContext;
import com.psddev.cms.tool.page.AddEmbeddedContent;
import com.psddev.cms.tool.page.content.Field;
import com.psddev.cms.ui.LocalizationContext;
import com.psddev.cms.ui.ToolLocalization;
import com.psddev.cms.ui.ToolRequest;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectIndex;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.db.State;
import com.psddev.dari.db.Trigger;
import com.psddev.dari.html.FlowFlowElement;
import com.psddev.dari.html.content.FlowContent;
import com.psddev.dari.html.text.AElement;
import com.psddev.dari.util.CompactMap;
import com.psddev.dari.util.JspUtils;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.TypeReference;
import com.psddev.dari.web.UrlBuilder;
import com.psddev.dari.web.WebRequest;
import com.psddev.theme.Config;
import com.psddev.theme.StyleGroup;
import com.psddev.theme.StyleGroupObject;
import com.psddev.theme.StyleGroupProvider;
import org.apache.commons.lang3.StringUtils;

import static com.psddev.cms.ui.Components.*;

public class StyleGroupEmbeddedContentCreator implements EmbeddedContentCreator {

    private static final String TEMPLATE_MODULE_TYPE_PARAMETER = "templateModuleType";
    private static final TypeReference<Map<String, Object>> MAP_STRING_OBJECT_REFERENCE = new TypeReference<Map<String, Object>>() {

    };

    public Object createObject(String data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dataJson = (Map<String, Object>) ObjectUtils.fromJson(data);
        Object object = ObjectType.getInstance(ObjectUtils.to(UUID.class, dataJson.get("typeId"))).createObject(null);

        State state = State.getInstance(object);
        if (state == null) {
            return object;
        }
        state.put(
                ObjectUtils.to(String.class, dataJson.get("key")),
                ObjectUtils.to(String.class, dataJson.get("value")));

        for (Map.Entry<String, Object> entry : dataJson.entrySet()) {
            String entryKey = entry.getKey();

            if (state.entrySet().stream().anyMatch(e -> entryKey.equals(e.getKey()))) {
                Object selected = Query.fromAll().where("_id = ?", entry.getValue()).first();

                if (selected != null) {
                    state.put(ObjectUtils.to(String.class, entryKey), selected);
                }
            }
        }

        Object simpleValues = dataJson.get("simpleValues");
        if (simpleValues != null && !("".equals(simpleValues))) {
            state.putAll(ObjectUtils.to(MAP_STRING_OBJECT_REFERENCE, dataJson.get("simpleValues")));
        }

        return object;
    }

    @Override
    public void writeHtml(ToolPageContext page, ObjectField field, String selectedTypeName) throws IOException {

        boolean isStyleGroup = false;
        Object content = page.findOrReserve();
        List<StyleGroup> styleGroups = new ArrayList<>();
        if (content instanceof Recordable && ((Recordable) content).isInstantiableTo(StyleGroup.class)) {
            isStyleGroup = true;
        } else {
            styleGroups = StyleGroupProvider.getAllStyleGroups(page);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Content Type Logic.
        String containerTypeId = WebRequest.getCurrent().getParameter(String.class, "containerTypeId");
        String currentTypeId = WebRequest.getCurrent().getParameter(String.class, "typeId");
        if (containerTypeId == null) {
            containerTypeId = currentTypeId;
        }

        ObjectType objectType = containerTypeId == null ? null : Query.from(ObjectType.class).where("_id = ?", containerTypeId).first();

        if (objectType != null) {
            // Filter Style Groups
            List<StyleGroup> filteredStyleGroups = new ArrayList<>();
            List<CuratedStyleGroup> globalStyleGroups = SiteSettings.get(null, e -> e.as(StyleGroupSettings.class)).getCuratedStyleGroups();

            if (styleGroups.isEmpty()) {
                filterStyleGroups(globalStyleGroups, filteredStyleGroups, objectType);
            } else {
                filterStyleGroups(styleGroups, filteredStyleGroups, objectType);
            }
            styleGroups = filteredStyleGroups;
        }
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        List<ObjectType> types = Field.getFormDisplayTypes(field, null);
        Config config = Config.getInstance();
        Map<String, List<TemplateStyle>> templateStylesByName = new HashMap<>();
        Map<String, TemplateStyle> templateStylesByTemplate = new HashMap<>();
        Set<String> templateKeys = new HashSet<>();
        List<TypeStyle> typeStyles = new ArrayList<>();
        List<ObjectType> existingTypes = new ArrayList<>();
        Map<ObjectType, TypeStyle> sharedTypeStyles = new HashMap<>();

        ToolRequest currentToolRequest = WebRequest.getCurrent().as(ToolRequest.class);
        Site site = currentToolRequest.getCurrentSite();
        List<String> recentlyUsedStyleNames = Optional.ofNullable(Query.from(ToolUserRecentlyUsed.class)
                        .where("user = ?", currentToolRequest.getCurrentUser())
                        .and("site = ?", site != null ? site : Query.MISSING_VALUE)
                        .and("type = ?", Query.MISSING_VALUE)
                        .first())
                .map(ToolUserRecentlyUsed::getTemplateStylesByFieldName)
                .map(map -> map.get(field.getInternalName()))
                .orElse(Collections.emptyList());
        List<TemplateStyle> recentlyUsedTemplateStyles = new ArrayList<>();
        List<TypeStyle> recentlyUsedTypeStyles = new ArrayList<>();

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        List<String> styleGroupTemplateValues = new ArrayList<>();
        if (!ObjectUtils.isBlank(styleGroups)) {
            List<ObjectType> newTypes = new ArrayList<>();
            for (StyleGroup group : styleGroups) {
                List<StyleGroupObject> styleGroupObjects = group.getStyleGroupObjects();
                for (StyleGroupObject styleGroupObject : styleGroupObjects) {
                    ObjectType objType = styleGroupObject.getState().getType();
                    newTypes.add(objType);

                    Map<String, Map<String, Object>> configStyles = config.getStylesByDataModel(objType.createObject(null));
                    for (Map.Entry<String, Map<String, Object>> entry : configStyles.entrySet()) {
                        String key = config.getFieldPrefix() + Config.createTemplateKey(entry.getKey()) + Config.TEMPLATE_FIELD_NAME;
                        Optional.ofNullable(styleGroupObject.getState())
                                .map(state -> state.get(key))
                                .map(String::valueOf)
                                .ifPresent(styleGroupTemplateValues::add);
                        break;
                    }
                }
            }
            types = newTypes;
        }
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        for (ObjectType type : types) {
            Object obj = type.createObject(null);
            Map<String, Map<String, Object>> configStyles = config.getStylesByDataModel(obj);
            boolean hasStyles = false;
            String name = ToolLocalization.text(type, "displayName");
            UUID typeId = type.getId();

            if (configStyles != null) {
                for (Map.Entry<String, Map<String, Object>> entry : configStyles.entrySet()) {
                    Map<String, Object> configStyle = entry.getValue();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> templates = (List<Map<String, Object>>) configStyle.get("templates");
                    String entryKey = entry.getKey();

                    if (templates == null || entryKey.contains(".amp.") || entryKey.contains(".native.")) {
                        continue;
                    }

                    String key = config.getFieldPrefix() + Config.createTemplateKey(entryKey) + Config.TEMPLATE_FIELD_NAME;
                    templateKeys.add(key);
                    List<TemplateStyle> styles = new ArrayList<>();
                    for (Map<String, Object> t : templates) {
                        String examplePath = ObjectUtils.to(String.class, t.get("example"));
                        if (Boolean.TRUE.equals(!ObjectUtils.to(boolean.class, t.get("hidden"))
                                && !StringUtils.isBlank(examplePath))) {
                            TemplateStyle templateStyle = new TemplateStyle(type, key, configStyle, t);
                            String templateName = ObjectUtils.to(String.class, t.get("template"));

                            if (styleGroups.isEmpty() || styleGroupTemplateValues.contains(templateName)) {
                                styles.add(templateStyle);
                                templateStylesByTemplate.put(
                                        templateName,
                                        templateStyle);

                                if (recentlyUsedStyleNames.contains(typeId + templateName)) {
                                    recentlyUsedTemplateStyles.add(templateStyle);
                                }
                            }
                        }
                    }

                    if (!styles.isEmpty()) {
                        hasStyles = true;
                        templateStylesByName.put(name, styles);
                    }
                }
            }

            if (!hasStyles) {
                TypeStyle typeStyle = new TypeStyle(type);

                if (!existingTypes.contains(type)) {
                    typeStyles.add(typeStyle);
                    existingTypes.add(type);
                }

                if (recentlyUsedStyleNames.contains(typeId.toString())) {
                    recentlyUsedTypeStyles.add(typeStyle);
                }
            }
        }

        String allLabel = ToolLocalization.text(getClass(), "title.all");
        String recentlyUsedLabel = ToolLocalization.text(getClass(), "title.recentlyUsed");
        String miscellaneousLabel = ToolLocalization.text(getClass(), "title.miscellaneous");

        Map<String, List<TemplateStyle>> templateStylesByGroup;
        Map<String, Map<String, List<TemplateStyle>>> templateStylesByGroupAndType;
        Map<String, List<TypeStyle>> typeStylesByGroup;

        if (isStyleGroup) {
            templateStylesByGroup = new CompactMap<>();
            templateStylesByGroupAndType = new CompactMap<>();
            typeStylesByGroup = new CompactMap<>();
        } else {
            templateStylesByGroup = getTemplateStylesByGroup(
                    styleGroups,
                    templateKeys,
                    templateStylesByTemplate,
                    ImmutableSet.<String>builder()
                            .add(allLabel)
                            .add(recentlyUsedLabel)
                            .add(miscellaneousLabel)
                            .addAll(templateStylesByName.keySet())
                            .build());
            templateStylesByGroupAndType = getTemplateStylesByGroupAndType(
                    styleGroups,
                    templateKeys,
                    templateStylesByTemplate,
                    templateStylesByName.keySet());
            typeStylesByGroup = getTypeStylesByGroup(styleGroups, typeStyles);
        }

        // Obtain selected style group module type if available
        String selectedTemplateModuleType = WebRequest.getCurrent()
                .getParameter(String.class, TEMPLATE_MODULE_TYPE_PARAMETER);

        String finalContainerTypeId = containerTypeId;
        page.write(DIV.className("StyleEmbeddedContent")
                .with(
                        H1.className("Widget-title")
                                .with(ToolLocalization.text(getClass(), "title.add")),
                        DIV.className("StyleEmbeddedContent-content")
                                .with(
                                        DIV.className("StyleEmbeddedContent-nav")
                                                .with(SPAN.className("StyleEmbeddedContent-search SearchInput")
                                                        .with(
                                                                LABEL.attrFor(page.createId()),
                                                                INPUT.id(page.getId())
                                                                        .typeText()
                                                                        .placeholder("Search")))
                                                .with(UL.with(ul -> {
                                                    UUID typeId = State.getInstance(content).getTypeId();
                                                    String fieldName = field.getInternalName();
                                                    UrlBuilder urlBuilder = currentToolRequest.getPathBuilder(AddEmbeddedContent.PATH)
                                                            .setParameter(AddEmbeddedContent.TYPE_ID_PARAMETER, typeId)
                                                            .setParameter(AddEmbeddedContent.FIELD_NAME_PARAMETER, fieldName)
                                                            .setParameter(AddEmbeddedContent.CONTAINER_TYPE_ID_PARAMETER, finalContainerTypeId);

                                                    ul.add(LI.className(getClassNameIfSelected(allLabel, selectedTypeName))
                                                            .with(A
                                                                    .href(buildUrl(urlBuilder, "", ""))
                                                                    .with(allLabel)));

                                                    if (!recentlyUsedTemplateStyles.isEmpty() || !recentlyUsedTypeStyles.isEmpty()) {
                                                        ul.add(LI.className(getClassNameIfSelected(recentlyUsedLabel, selectedTypeName))
                                                                .with(A
                                                                        .href(buildUrl(urlBuilder, recentlyUsedLabel, ""))
                                                                        .with(recentlyUsedLabel)));
                                                    }

                                                    if (!templateStylesByGroup.isEmpty()) {
                                                        List<String> templateGroup = new ArrayList<>(templateStylesByGroup.keySet());
                                                        templateGroup.sort(Comparator.reverseOrder());

                                                        for (String templateStyleName : templateGroup) {
                                                            ul.add(LI.className(getClassNameIfSelected(templateStyleName, selectedTypeName))
                                                                    .with(DIV
                                                                            .className("StyleEmbeddedContent-subnav-header")
                                                                            .attr("style-group", templateStyleName)
                                                                            .with(SPAN.className("subnav-title")
                                                                                    .with(templateStyleName))
                                                                            .with(SPAN.className("subnav-icon")))
                                                                    .with(UL.className("StyleEmbeddedContent-subnav")
                                                                            .with(nestedUl -> {
                                                                                for (Map.Entry<String, List<TemplateStyle>> entry : templateStylesByGroupAndType.get(templateStyleName).entrySet()) {
                                                                                    nestedUl.add(LI.className(templateStyleName.equals(selectedTypeName)
                                                                                                    ? getClassNameIfSelected(entry.getKey(), selectedTemplateModuleType)
                                                                                                    : "")
                                                                                            .with(A
                                                                                                    .href(buildUrl(urlBuilder, templateStyleName, entry.getKey()))
                                                                                                    .with(entry.getKey())));
                                                                                }

                                                                                if (typeStylesByGroup.containsKey(templateStyleName)) {
                                                                                    for (TypeStyle typeStyle : typeStylesByGroup.get(templateStyleName)) {
                                                                                        nestedUl.add(LI.with(typeStyle.createTypeStyleLink()));
                                                                                    }
                                                                                }
                                                                            })));
                                                        }
                                                    } else {
                                                        List<String> styleNames = templateStylesByName
                                                                .keySet()
                                                                .stream()
                                                                .sorted()
                                                                .collect(Collectors.toList());
                                                        for (String name : styleNames) {
                                                            ul.add(LI.className(getClassNameIfSelected(name, selectedTypeName))
                                                                    .with(A
                                                                            .href(buildUrl(urlBuilder, name, ""))
                                                                            .with(name)));
                                                        }

                                                        if (!typeStyles.isEmpty()) {
                                                            ul.add(LI.className(getClassNameIfSelected(miscellaneousLabel, selectedTypeName))
                                                                    .with(A
                                                                            .href(buildUrl(urlBuilder, miscellaneousLabel, ""))
                                                                            .with(miscellaneousLabel)));
                                                        }
                                                    }
                                                })),
                                        DIV.className("StyleEmbeddedContent-main").with(main -> {
                                            boolean isShowAll = StringUtils.isBlank(selectedTypeName);
                                            boolean isRecentlyUsed = recentlyUsedLabel.equals(selectedTypeName);
                                            boolean isMisc = miscellaneousLabel.equals(selectedTypeName);

                                            if (isRecentlyUsed || isShowAll) {
                                                boolean hasRecentlyUsedTemplateStyles = !recentlyUsedTemplateStyles.isEmpty();
                                                if (hasRecentlyUsedTemplateStyles) {
                                                    main.add(createTemplateStylesContainer(
                                                            page,
                                                            Collections.singletonMap(recentlyUsedLabel, recentlyUsedTemplateStyles),
                                                            sharedTypeStyles));
                                                }

                                                if (!recentlyUsedTypeStyles.isEmpty()) {
                                                    if (!hasRecentlyUsedTemplateStyles) {
                                                        main.add(H2.with(recentlyUsedLabel));
                                                    }
                                                    main.add(createTypeStylesList(recentlyUsedTypeStyles));
                                                }
                                            }

                                            if (!isMisc && !isRecentlyUsed) {
                                                // Combine style maps into "templateStylesByGroup" so creating containers is more concise

                                                Map<String, List<TemplateStyle>> tempTemplateStylesByGroup = new LinkedHashMap<>(new TreeMap<>(templateStylesByName));
                                                List<String> keySet = new ArrayList<>(templateStylesByGroup.keySet());
                                                keySet.sort(Comparator.reverseOrder());
                                                keySet.forEach(key -> tempTemplateStylesByGroup.put(key, templateStylesByGroup.get(key)));

                                                if (StringUtils.isBlank(selectedTemplateModuleType)) {
                                                    main.add(createTemplateStylesContainer(
                                                            page,
                                                            isShowAll ? tempTemplateStylesByGroup : Collections.singletonMap(
                                                                    selectedTypeName,
                                                                    tempTemplateStylesByGroup.get(selectedTypeName)),
                                                            sharedTypeStyles));
                                                } else {
                                                    main.add(createTemplateStylesContainer(
                                                            page,
                                                            isShowAll ? tempTemplateStylesByGroup : Collections.singletonMap(
                                                                    selectedTypeName + " > " + selectedTemplateModuleType,
                                                                    templateStylesByGroupAndType
                                                                            .get(selectedTypeName)
                                                                            .get(selectedTemplateModuleType)),
                                                            sharedTypeStyles));
                                                }
                                            }

                                            if ((isMisc || isShowAll) && !typeStyles.isEmpty()) {
                                                main.add(H2.with(miscellaneousLabel));
                                                Collections.sort(typeStyles);
                                                main.add(createTypeStylesList(typeStyles));
                                            }
                                        }))));
    }

    private void filterStyleGroups(List<? extends StyleGroup> styleGroups, List<StyleGroup> filteredStyleGroups, ObjectType objectType) {
        for (StyleGroup group : styleGroups) {
            List<ObjectType> types = Optional.ofNullable(group.as(CuratedStyleGroupModification.class))
                    .map(CuratedStyleGroupModification::getTypes)
                    .orElse(null);

            if (!ObjectUtils.isBlank(types) && types.contains(objectType)) {
                filteredStyleGroups.add(group);
            }
        }
    }

    private FlowFlowElement createTemplateStylesContainer(
            ToolPageContext page,
            Map<String, List<TemplateStyle>> templateStylesByHeader,
            Map<ObjectType, TypeStyle> sharedTypeStyles) {

        Set<String> headers = templateStylesByHeader.keySet();

        FlowFlowElement container = DIV;

        for (String header : headers) {
            List<TemplateStyle> templateStyles = templateStylesByHeader.get(header);
            FlowFlowElement styleContainer = DIV.className("StyleContainer").attr("name", header).with(
                    H2.with(header),
                    UL.className("Grid Grid-modified").with(ul -> {
                        for (TemplateStyle style : templateStyles) {
                            TypeStyle typeStyle = sharedTypeStyles.get(style.type);
                            if (typeStyle != null) {
                                ul.add(LI.with(typeStyle.createTypeStyleCard(page)));
                                sharedTypeStyles.remove(style.type);
                                break;
                            }
                        }
                    }),
                    DIV.className("Shared-divider"),
                    UL.className("Grid").with(ul -> {
                        for (TemplateStyle style : templateStyles) {
                            TypeStyle typeStyle = sharedTypeStyles.get(style.type);
                            if (typeStyle != null) {
                                ul.add(LI.with(typeStyle.createTypeStyleCard(page)));
                                sharedTypeStyles.remove(style.type);
                                break;
                            }
                        }
                        for (TemplateStyle style : templateStyles) {
                            ul.add(LI.with(style.createTemplateStyleCard(page)));
                        }
                    })
            );
            container = container.with(styleContainer);
        }

        return container;
    }

    private FlowContent createTypeStylesList(List<TypeStyle> typeStyles) {
        return UL.className("LinkTable").with(ul -> {
            for (TypeStyle typeStyle : typeStyles) {
                ul.add(LI.with(typeStyle.createTypeStyleLink()));
            }
        });
    }

    private String getClassNameIfSelected(String typeName, String selectedTypeName) {
        if (StringUtils.isBlank(selectedTypeName)) {
            selectedTypeName = ToolLocalization.text(getClass(), "title.all");
        }

        if (typeName.equals(selectedTypeName)) {
            return "is-selected";
        }

        return "";
    }

    private String buildUrl(UrlBuilder builder, String templateStyle, String templateType) {
        return builder
                .setParameter(AddEmbeddedContent.TEMPLATE_STYLE_PARAMETER, templateStyle)
                .setParameter(TEMPLATE_MODULE_TYPE_PARAMETER, templateType)
                .build();
    }

    /**
     * Uses the list of {@link StyleGroup's} and returns a map of TemplateStyles by the StyleGroup's name. Checks if the
     * {@link StyleGroupObject} contains any of the templateKeys, and if so create a copy of the TemplateStyle with that
     * key value. Add the simple values from the {@link StyleGroupObject} to the TemplateStyle so values can be reused.
     */
    private static Map<String, List<TemplateStyle>> getTemplateStylesByGroup(
            List<StyleGroup> styleGroups,
            Set<String> templateKeys,
            Map<String, TemplateStyle> templateStylesByTemplate,
            Set<String> existingKeys) {

        Map<String, List<TemplateStyle>> templateStylesByGroup = new HashMap<>();
        if (templateKeys.isEmpty() || templateStylesByTemplate.isEmpty()) {
            return templateStylesByGroup;
        }

        for (StyleGroup styleGroup : styleGroups) {
            for (StyleGroupObject styleGroupObject : styleGroup.getStyleGroupObjects()) {
                State state = styleGroupObject.getState();
                for (String templateKey : templateKeys) {
                    if (state.containsKey(templateKey)) {
                        String template = ObjectUtils.to(String.class, state.get(templateKey));
                        TemplateStyle templateStyle = templateStylesByTemplate.get(template);

                        if (templateStyle != null) {
                            Map<String, Object> simpleValues = Optional.ofNullable(State
                                            .getInstance(copyEmbedded(state.getOriginalObject())))
                                    .map(State::getSimpleValues)
                                    .orElse(new HashMap<>());

                            String key = styleGroup.getStyleGroupName();

                            if (existingKeys.contains(key)) {
                                key = ToolLocalization.text(
                                        new LocalizationContext(
                                                StyleGroupEmbeddedContentCreator.class,
                                                ImmutableMap.of("key", key)),
                                        "title.custom");
                            }

                            templateStylesByGroup
                                    .computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(new TemplateStyle(templateStyle, simpleValues));

                            break;
                        }
                    }
                }
            }
        }

        return templateStylesByGroup;
    }

    private static Map<String, Map<String, List<TemplateStyle>>> getTemplateStylesByGroupAndType(
            List<StyleGroup> styleGroups,
            Set<String> templateKeys,
            Map<String, TemplateStyle> templateStylesByTemplate,
            Set<String> existingKeys) {

        Map<String, Map<String, List<TemplateStyle>>> templateStylesByGroupAndType = new HashMap<>();
        if (templateKeys.isEmpty() || templateStylesByTemplate.isEmpty()) {
            return templateStylesByGroupAndType;
        }

        for (StyleGroup styleGroup : styleGroups) {
            for (StyleGroupObject styleGroupObject : styleGroup.getStyleGroupObjects()) {
                State state = styleGroupObject.getState();
                for (String templateKey : templateKeys) {
                    if (state.containsKey(templateKey)) {
                        String template = ObjectUtils.to(String.class, state.get(templateKey));
                        TemplateStyle templateStyle = templateStylesByTemplate.get(template);

                        if (templateStyle != null) {
                            Map<String, Object> simpleValues = Optional.ofNullable(State
                                            .getInstance(copyEmbedded(state.getOriginalObject())))
                                    .map(State::getSimpleValues)
                                    .orElse(new HashMap<>());

                            String key = styleGroup.getStyleGroupName();

                            if (existingKeys.contains(key)) {
                                key = ToolLocalization.text(
                                        new LocalizationContext(
                                                StyleGroupEmbeddedContentCreator.class,
                                                ImmutableMap.of("key", key)),
                                        "title.custom");
                            }

                            if (!templateStylesByGroupAndType.containsKey(key)) {
                                templateStylesByGroupAndType.put(key, new TreeMap<>());
                            }

                            String typeKey = Optional.ofNullable(templateStyle.type)
                                    .map(ObjectType::getLabel)
                                    .orElse(null);
                            if (!StringUtils.isBlank(typeKey)) {
                                if (templateStylesByGroupAndType.get(key).containsKey(typeKey)) {
                                    templateStylesByGroupAndType.get(key).get(typeKey).add(new TemplateStyle(templateStyle, simpleValues));
                                } else {
                                    List<TemplateStyle> newTemplateStyles = new ArrayList<>();
                                    newTemplateStyles.add(new TemplateStyle(templateStyle, simpleValues));
                                    templateStylesByGroupAndType.get(key).put(typeKey, newTemplateStyles);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        return templateStylesByGroupAndType;
    }

    /**
     * Uses the list of {@link StyleGroup's} and returns a list of TypeStyles by the StyleGroup's name. Checks if the
     * {@link StyleGroupObject} contains any of the types, and if so create a copy of the TypeStyle with that
     * key value.
     */
    private static Map<String, List<TypeStyle>> getTypeStylesByGroup(
            List<StyleGroup> styleGroups,
            List<TypeStyle> typeStyles) {

        Map<String, List<TypeStyle>> typeStylesByGroup = new HashMap<>();

        for (StyleGroup styleGroup : styleGroups) {
            for (StyleGroupObject styleGroupObject : styleGroup.getStyleGroupObjects()) {
                ObjectType styleGroupObjectType = Optional.ofNullable(styleGroupObject.getState())
                        .map(State::getType)
                        .orElse(null);

                if (styleGroupObjectType != null) {
                    for (StyleGroupEmbeddedContentCreator.TypeStyle typeStyle : typeStyles) {
                        if (styleGroupObjectType.equals(typeStyle.type)) {
                            String key = styleGroup.getStyleGroupName();

                            if (!typeStylesByGroup.containsKey(key)) {
                                List<TypeStyle> newTypeStyles = new ArrayList<>();
                                newTypeStyles.add(typeStyle);
                                typeStylesByGroup.put(key, newTypeStyles);
                            } else {
                                typeStylesByGroup.get(key).add(typeStyle);
                            }
                        }
                    }
                }
            }
        }

        return typeStylesByGroup;
    }

    /**
     * Mirrored from {@link Copyable#copy}.
     */
    @SuppressWarnings("unchecked")
    private static <T> T copyEmbedded(Object source) {
        Preconditions.checkNotNull(source, "source");

        State sourceState = State.getInstance(source);
        ObjectType targetType = sourceState.getDatabase().getEnvironment().getTypeByClass(source.getClass());

        Preconditions.checkState(targetType != null, "Copy failed! Could not determine copy target type!");

        Object destination = targetType.createObject(null);
        State destinationState = State.getInstance(destination);
        Content.ObjectModification destinationContent = destinationState.as(Content.ObjectModification.class);

        destinationState.putAll(getCopyableRawFieldValues(sourceState));
        destinationState.setId(null);
        destinationState.setStatus(null);

        if (!ObjectUtils.equals(sourceState.getType(), targetType)) {
            destinationState.setType(sourceState.getType());
            // Unset all visibility indexes defined by the source ObjectType
            Stream.concat(
                            destinationState.getIndexes().stream(),
                            destinationState.getDatabase().getEnvironment().getIndexes().stream())
                    .filter(ObjectIndex::isVisibility)
                    .map(ObjectIndex::getFields)
                    .flatMap(Collection::stream)
                    .forEach(destinationState::remove);

            // update dari.visibilities while source ObjectType is set
            destinationState.getVisibilityAwareTypeId();
        }

        destinationState.setType(targetType);

        Directory.ObjectModification directoryModification = destinationState.as(Directory.ObjectModification.class);

        //Clear Global Site Paths
        directoryModification.clearSitePaths(null);

        //Clear All Site Paths
        for (Site site : Site.Static.findAll()) {
            directoryModification.clearSitePaths(site);
        }

        // Unset all visibility indexes defined by the target ObjectType
        Stream.concat(
                        destinationState.getIndexes().stream(),
                        destinationState.getDatabase().getEnvironment().getIndexes().stream())
                .filter(ObjectIndex::isVisibility)
                .map(ObjectIndex::getFields)
                .flatMap(Collection::stream)
                .forEach(destinationState::remove);

        // update dari.visibilities while target ObjectType is set
        destinationState.getVisibilityAwareTypeId();

        // Clear publishUser, updateUser, publishDate, and updateDate.
        destinationContent.setPublishUser(null);
        destinationContent.setUpdateUser(null);
        destinationContent.setUpdateDate(null);
        destinationContent.setPublishDate(null);

        // If it or any of its modifications are copyable, fire onCopy()
        destinationState.fireTrigger(new CopyTrigger(source));

        return (T) destination;
    }

    /**
     * Gets the raw field values with empty ids so that they are different after copy. {@link State#getRawValues()} is
     * used so that invisible objects are included.
     */
    @Nonnull
    private static Map<String, Object> getCopyableRawFieldValues(State state) {

        Map<String, Object> originalStateFieldValues = state.getSimpleValues();
        Map<String, Object> resetStateFieldValues = new CompactMap<>();

        originalStateFieldValues.forEach((key, value) ->
                resetStateFieldValues.put(key, getObjectFieldValue(value)));

        return resetStateFieldValues;
    }

    @SuppressWarnings("unchecked")
    private static Object getObjectFieldValue(Object object) {
        if (object instanceof State || object instanceof Recordable) {
            State state = State.getInstance(object);
            if (state.isEmbedded() || state.getType().isEmbedded()) {
                state.setId(null);
            }
            state.putAll(getCopyableRawFieldValues(state));
            return state.as(object.getClass());
        } else if (object instanceof Map) {
            Map<String, Object> map = new CompactMap<>();
            map.putAll((Map<String, Object>) object);
            map.remove(State.ID_KEY);
            return map;
        } else if (object instanceof Collection) {
            return ((Collection) object).stream()
                    .map(StyleGroupEmbeddedContentCreator::getObjectFieldValue)
                    .collect(Collectors.toList());
        } else {
            return object;
        }
    }

    /**
     * Executes {@link Copyable#onCopy} on the object and for each {@link com.psddev.dari.db.Modification}.
     */
    private static class CopyTrigger implements Trigger {

        private Object source;

        public CopyTrigger(Object source) {
            this.source = source;
        }

        @Override
        public void execute(Object object) {
            if (object instanceof Copyable) {
                ((Copyable) object).onCopy(source);
            }
        }
    }

    private static class TemplateStyle {

        private final ObjectType type;
        private final String key;
        private final Map<String, Object> configStyle;
        private final Map<String, Object> template;
        private final Map<String, Object> simpleValues;

        public TemplateStyle(
                ObjectType type,
                String key,
                Map<String, Object> configStyle,
                Map<String, Object> template) {
            this.type = type;
            this.key = key;
            this.configStyle = configStyle;
            this.template = template;
            this.simpleValues = null;
        }

        public TemplateStyle(TemplateStyle templateStyle, Map<String, Object> simpleValues) {
            this.type = templateStyle.type;
            this.key = templateStyle.key;
            this.configStyle = templateStyle.configStyle;
            this.template = templateStyle.template;
            this.simpleValues = simpleValues;
        }

        public FlowContent createTemplateStyleCard(ToolPageContext page) {
            String examplePreview = ObjectUtils.to(String.class, template.get("examplePreview"));

            int width = ObjectUtils.to(int.class, template.get("width"));

            if (width <= 0) {
                width = ObjectUtils.to(int.class, configStyle.get("width"));

                if (width <= 0) {
                    width = 1440;
                }
            }

            int height = ObjectUtils.to(int.class, template.get("height"));

            if (height <= 0) {
                height = ObjectUtils.to(int.class, configStyle.get("height"));

                if (height <= 0) {
                    height = 900;
                }
            }

            double scale = 250.0 / Math.max(width, height);
            double scaledHeight = height * scale;
            double scaledWidth = width * scale;

            return A.className("Card")
                    .href("#")
                    .attr("data-add-embedded-content-data", ObjectUtils.toJson(ImmutableMap.of(
                            "typeId", type.getId().toString(),
                            "key", key,
                            "value", template.get("template"),
                            "simpleValues", simpleValues != null ? simpleValues : "")))
                    .with(
                            DIV.classList(!StringUtils.isBlank(examplePreview) ? "Card-media" : "Card-media is-missing")
                                    .with(div -> {
                                        if (!StringUtils.isBlank(examplePreview)) {
                                            div.add(DIV
                                                    .style(page.cssString(
                                                            "background", "#fff",
                                                            "height", (scaledHeight) + "px",
                                                            "overflow", "hidden",
                                                            "pointer-events", "none",
                                                            "width", (scaledWidth) + "px"))
                                                    .with(IMG
                                                            .src(Optional.ofNullable(WebRequest.getCurrent()
                                                                            .as(ToolRequest.class)
                                                                            .getCurrentSite())
                                                                    .map(Site::getEffectivePreviewUrl)
                                                                    .orElseGet(() -> "//" + JspUtils.getHost(page.getRequest()))
                                                                    + examplePreview)));
                                        }
                                    }),
                            DIV.className("Card-title").with(template.get("displayName").toString()));
        }
    }

    private static class TypeStyle implements Comparable<TypeStyle> {

        private final ObjectType type;

        public TypeStyle(ObjectType type) {
            this.type = type;
        }

        public AElement createTypeStyleLink() {
            return A
                    .href("#")
                    .attr("data-add-embedded-content-data", ObjectUtils.toJson(ImmutableMap.of(
                            "typeId", type.getId().toString())))
                    .with(ToolLocalization.text(type, "displayName"));
        }

        public FlowContent createTypeStyleCard(ToolPageContext page) {
            int width = 1440;
            int height = 900;
            double scale = 250.0 / width;
            double scaledHeight = height * scale;
            double scaledWidth = width * scale;

            return A.className("Card")
                    .href("#")
                    .attr("data-add-embedded-content-data", ObjectUtils.toJson(ImmutableMap.of(
                            "typeId", type.getId().toString())))
                    .with(
                            DIV.classList("Card-media is-missing")
                                    .style(page.cssString(
                                            "height", (scaledHeight) + "px",
                                            "width", (scaledWidth) + "px")),
                            DIV.className("Card-title").with(ToolLocalization.text(type, "displayName")));
        }

        @Override
        public int compareTo(TypeStyle other) {
            return ObjectUtils.compare(
                    ToolLocalization.text(type, "displayName"),
                    ToolLocalization.text(other.type, "displayName"),
                    false);
        }
    }
}
