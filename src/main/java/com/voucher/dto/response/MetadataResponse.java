package com.voucher.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MetadataResponse {

    private String name;
    private String description;
    private String image;
    private List<Attribute> attributes;

    @Getter
    @Builder
    public static class Attribute {
        @JsonProperty("trait_type")
        private String traitType;
        private Object value;
    }
}
