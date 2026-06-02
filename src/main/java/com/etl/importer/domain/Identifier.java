package com.etl.importer.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Identifier {
    private String use;
    private String type;
    private String value;
    private Assigner assigner;
    private String system;

    public Identifier(String type, String value, String system) {
        this.use = NameUse.OFFICIAL.name();
        this.type = type;
        this.value = value;
        this.system = system;
        this.assigner = new Assigner();
    }
}

