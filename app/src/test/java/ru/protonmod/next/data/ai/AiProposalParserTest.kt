/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProposalParserTest {
    @Test
    fun `parses interactive profile proposal`() {
        val proposal = AiProposalParser.parse(
            """{"title":"Fast profile","summary":"Review this profile","actions":[{"action":"create_profile","name":"Fast NL","country":"NL","port":443}]}"""
        )

        assertEquals("Fast profile", proposal.title)
        assertEquals(1, proposal.actions.size)
        assertEquals("create_profile", proposal.actions.single().type)
        assertFalse(proposal.actions.single().destructive)
        assertEquals("Fast NL", proposal.actions.single().profilePreview?.name)
        assertEquals("NL", proposal.actions.single().profilePreview?.country)
        assertEquals(443, proposal.actions.single().profilePreview?.port)
    }

    @Test
    fun `marks profile deletion as destructive`() {
        val proposal = AiProposalParser.parse(
            """{"action":"delete_profile","profileId":"profile-1","profileName":"Old profile"}"""
        )

        assertTrue(proposal.actions.single().destructive)
        assertEquals("Delete profile", proposal.actions.single().title)
    }

    @Test
    fun `accepts array response for compatibility`() {
        val proposal = AiProposalParser.parse(
            """[{"action":"refresh_servers"},{"action":"set_setting","key":"tor_mode","value":true}]"""
        )

        assertEquals(2, proposal.actions.size)
        assertEquals("refresh_servers", proposal.actions.first().type)
    }
}
