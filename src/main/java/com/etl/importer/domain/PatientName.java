package com.etl.importer.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PatientName {
    private String use = NameUse.OFFICIAL.name();
    private String family;
    private List<String> given;
    private List<String> prefix = new ArrayList<>();
    private List<String> suffix = new ArrayList<>();

    public PatientName(List<String> given) {
        this.given = given;
        this.use = NameUse.OFFICIAL.name();
        this.family = null;
        this.prefix = new ArrayList<>();
        this.suffix = new ArrayList<>();
    }
}
