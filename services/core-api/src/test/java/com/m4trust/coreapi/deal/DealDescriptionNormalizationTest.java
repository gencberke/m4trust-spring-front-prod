package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DealDescriptionNormalizationTest {

    @Test
    void blankCreateDescriptionsCollapseToNull() {
        assertNull(new CreateDealRequest("Deal", null).description());
        assertNull(new CreateDealRequest("Deal", "").description());
        assertNull(new CreateDealRequest("Deal", "   \n\t ").description());
        assertEquals(" keeps inner content ",
                new CreateDealRequest("Deal", " keeps inner content ")
                        .description());
    }

    @Test
    void blankUpdateDescriptionsCollapseToNullAndStayExplicit() {
        UpdateDealRequest request = new UpdateDealRequest();
        request.setDescription("   ");
        assertTrue(request.descriptionPresent());
        assertNull(request.description());

        request.setDescription("kept");
        assertEquals("kept", request.description());
    }
}
