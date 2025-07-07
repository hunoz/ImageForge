package dev.popaxe.dave.userapi.models.aws

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class PolicyTest {

    @Test
    fun `test statement behavior is as expected`() {
        val statement =
            Statement(
                "Allow",
                mutableListOf("ssm:GetParameter"),
                mutableListOf("aws:ssm:region:account-id:parameter/path/to/parameter"),
            )

        assert(statement.effect == "Allow")
        assert(statement.actions.size == 1)
        assert(statement.actions[0] == "ssm:GetParameter")
        assert(statement.resources.size == 1)
        assert(statement.resources[0] == "aws:ssm:region:account-id:parameter/path/to/parameter")

        assertDoesNotThrow {
            statement.actions.add("ssm:GetParameters")
            statement.resources.add("aws:ssm:region:account-id:parameter/path/to/parameter2")
        }

        assert(statement.actions.size == 2)
        assert(statement.actions[1] == "ssm:GetParameters")
        assert(statement.resources.size == 2)
        assert(statement.resources[1] == "aws:ssm:region:account-id:parameter/path/to/parameter2")

        assertDoesNotThrow {
            statement.actions.remove("ssm:GetParameter")
            statement.resources.remove("aws:ssm:region:account-id:parameter/path/to/parameter")
        }

        assert(statement.actions.size == 1)
        assert(statement.actions[0] == "ssm:GetParameters")
        assert(statement.resources.size == 1)
        assert(statement.resources[0] == "aws:ssm:region:account-id:parameter/path/to/parameter2")
    }

    @Test
    fun `test document behavior is as expected`() {
        val document =
            FederatedPermissionsPolicyDocument(
                "2012-10-17",
                mutableListOf(
                    Statement(
                        "Allow",
                        mutableListOf("ssm:GetParameter"),
                        mutableListOf("aws:ssm:region:account-id:parameter/path/to/parameter"),
                    )
                ),
            )

        assert(document.version == "2012-10-17")
        assert(document.statement.size == 1)
        assert(document.statement[0].effect == "Allow")
        assert(document.statement[0].actions.size == 1)
        assert(document.statement[0].actions[0] == "ssm:GetParameter")
        assert(document.statement[0].resources.size == 1)
        assert(
            document.statement[0].resources[0] ==
                "aws:ssm:region:account-id:parameter/path/to/parameter"
        )

        assertDoesNotThrow {
            document.statement.add(
                Statement(
                    "Allow",
                    mutableListOf("ssm:GetParameters"),
                    mutableListOf("aws:ssm:region:account-id:parameter/path/to/parameter2"),
                )
            )
        }

        assert(document.statement.size == 2)
        assert(document.statement[1].effect == "Allow")
        assert(document.statement[1].actions.size == 1)
        assert(document.statement[1].actions[0] == "ssm:GetParameters")
        assert(document.statement[1].resources.size == 1)
        assert(
            document.statement[1].resources[0] ==
                "aws:ssm:region:account-id:parameter/path/to/parameter2"
        )
    }
}
