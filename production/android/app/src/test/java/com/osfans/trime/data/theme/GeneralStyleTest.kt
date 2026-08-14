// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class GeneralStyleTest :
    BehaviorSpec({
        Given("Correct trime.yaml") {
            val dir = File("src/test/assets")

            When("loaded") {
                val node = Yaml.parseToYamlNode(File(dir, "trime.yaml").readText()).mapping!!
                val generalStyle = Theme.decode(node).generalStyle

                Then("it should not be null") {
                    generalStyle.autoCaps shouldBe false

                    generalStyle.candidateFont shouldBe listOf("han.ttf")
                    generalStyle.commentFont shouldBe listOf("comment.ttf")
                    generalStyle.hanbFont shouldBe listOf("hanb.ttf")
                    generalStyle.keyFont shouldBe listOf("symbol.ttf")
                    generalStyle.labelFont shouldBe listOf("label.ttf")
                    generalStyle.latinFont shouldBe listOf("latin.ttf")
                    generalStyle.symbolFont shouldBe listOf("symbol.ttf")
                    generalStyle.textFont shouldBe listOf("latin.ttf")
                }
            }

        }

        Given("Empty trime.yaml") {
            val dir = File("src/test/assets")

            When("loaded") {
                val node = Yaml.parseToYamlNode(File(dir, "incorrect.yaml").readText()).mapping!!
                val generalStyle = Theme.decode(node).generalStyle

                Then("with default value without exception") {
                    generalStyle.autoCaps shouldBe false
                    generalStyle.candidateBorder shouldBe 0
                    generalStyle.candidateFont shouldBe emptyList()
                    generalStyle.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    generalStyle.enterLabel shouldNotBe null
                    generalStyle.enterLabel.go shouldBe "go"
                }
            }

        }
    })
