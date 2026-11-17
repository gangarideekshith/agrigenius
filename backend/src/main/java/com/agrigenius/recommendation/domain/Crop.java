package com.agrigenius.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference crop. Codes match the crop model's class labels. */
@Entity
@Table(name = "crops")
public class Crop {

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name_en", nullable = false, length = 80)
    private String nameEn;

    protected Crop() {
        // for JPA
    }

    public Crop(String code, String nameEn) {
        this.code = code;
        this.nameEn = nameEn;
    }

    public String getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }
}
