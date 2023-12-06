package com.psddev.cms.theme;

import java.util.ArrayList;
import java.util.List;

import brightspot.theme.CuratedStyleGroup;
import com.psddev.cms.db.Content;
import com.psddev.cms.db.ToolUi;
import com.psddev.dari.db.Modification;
import com.psddev.dari.db.ObjectType;

@ToolUi.FieldDisplayOrder({
    "displayName",
    "types",
    "styles"
})
public class CuratedStyleGroupModification extends Modification<CuratedStyleGroup> {

    @Where(Content.TYPE_PREDICATE_STRING)
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

}