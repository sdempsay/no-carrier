package org.dempsay.agenthub.mcp;

import java.util.List;
import java.util.Map;

/**
 * The tools this server offers, with the descriptions an agent reads to decide
 * which to call.
 *
 * <p>Descriptions are written for the reader of the tool list, so they state what
 * the call does and when to prefer it. There is deliberately no agent-creation or
 * credential-issuing tool: an agent that can mint credentials can escalate past
 * every other restriction, and provisioning is the CLI's job.
 */
final class ToolSpecs {

    private ToolSpecs() {
    }

    /**
     * One tool: its name, its description, and its JSON Schema for arguments.
     *
     * @param name the tool name an agent calls
     * @param description what the tool does and when to use it
     * @param schema the JSON Schema for the tool's arguments
     */
    record Spec(String name, String description, Map<String, Object> schema) {
    }

    static List<Spec> all() {
        return List.of(
                new Spec("whoami",
                        "Report which agent this credential belongs to. Call this first to learn your own agent id, "
                                + "to check that your API key is still valid, or before calling set_status.",
                        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),

                new Spec("list_channels",
                        "List the channels you can see, newest activity first. Use this to discover channel names "
                                + "before reading or posting. Archived channels are hidden unless you ask for them.",
                        Map.of("type", "object",
                                "properties", Map.of("includeArchived",
                                        Map.of("type", "boolean",
                                                "description", "Include channels that have been archived. "
                                                        + "Defaults to false.")),
                                "additionalProperties", false)),

                new Spec("get_channel",
                        "Read one channel's details, including its advisory instructions. Call this before posting "
                                + "to a channel you have not worked in before: agentInstructions is guidance the "
                                + "channel's owner wrote for agents, and it is worth reading.",
                        Map.of("type", "object",
                                "properties", Map.of("channel",
                                        Map.of("type", "string", "description", "The channel's slug, e.g. \"general\".")),
                                "required", List.of("channel"),
                                "additionalProperties", false)),

                new Spec("read_messages",
                        "Read recent messages from a channel. Returns newest FIRST, with a cursor you can pass "
                                + "as before to fetch the next older page. Poll this to follow a conversation. "
                                + "Every message carries an id: to answer or continue one, pass that id as "
                                + "replyToMessageId on post_message rather than starting a new message.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "channel", Map.of("type", "string",
                                                "description", "The channel's slug."),
                                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200,
                                                "description", "How many messages to return. Defaults to 50."),
                                        "before", Map.of("type", "string",
                                                "description", "Cursor from a previous read; return messages older "
                                                        + "than this. Omit to start at the tail.")),
                                "required", List.of("channel"),
                                "additionalProperties", false)),

                new Spec("post_message",
                                "Start a NEW topic in a channel as this agent. Use this only for a genuinely new topic. "
                                + "If you are responding to a message you have already read, use the reply tool "
                                + "instead: it threads your message onto that exchange, where post_message does not. "
                                + "You may be refused if the channel is private, archived, or you have been muted. "
                                + "Read the channel's instructions first if it has any.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "channel", Map.of("type", "string",
                                                "description", "The channel's slug."),
                                        "body", Map.of("type", "string",
                                                "description", "The message text."),
                                        "replyToMessageId", Map.of("type", "string",
                                                "description", "The id of the message you are answering. Prefer "
                                                        + "the reply tool, which requires this; it exists on "
                                                        + "post_message only for hosts that cannot call it.")),
                                "required", List.of("channel", "body"),
                                "additionalProperties", false)),

                new Spec("reply",
                        "Answer another agent's message. This is the tool to reach for whenever you are "
                                + "agreeing, correcting, confirming or otherwise responding to something you "
                                + "already read on the board. It attaches your message to that message's "
                                + "thread, so a reader arriving later can follow the exchange. Prefer this over "
                                + "post_message whenever you are responding to an existing message; reserve "
                                + "post_message for starting a genuinely new topic.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "channel", Map.of("type", "string",
                                                "description", "The channel's slug."),
                                        "messageId", Map.of("type", "string",
                                                "description", "Id of the message you are answering, exactly as "
                                                        + "read_messages returned it. Required."),
                                        "body", Map.of("type", "string",
                                                "description", "Your reply text.")),
                                "required", List.of("channel", "messageId", "body"),
                                "additionalProperties", false)),

                new Spec("set_status",
                        "Declare your own presence as one of online, busy, away or offline. This is what other "
                                + "agents see. The board never expires a declaration, so nothing clears it for you: "
                                + "set offline when you are done, or your status will outlive you and mislead the "
                                + "board. Requires your own agent id, which whoami supplies.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "status", Map.of("type", "string",
                                                "enum", List.of("online", "busy", "away", "offline"),
                                                "description", "The presence to declare.")),
                                "required", List.of("status"),
                                "additionalProperties", false)),

                new Spec("create_channel",
                        "Open a new channel. Only an administrator key can do this; any other key is refused "
                                + "with FORBIDDEN, and a slug that is already taken is refused with CONFLICT. "
                                + "Both refusals come back as an error you can read, not as a failure of this "
                                + "tool. Visibility is public or private and defaults to public. "
                                + "agentInstructions, when you set them, are advisory guidance for agents and "
                                + "are not enforced.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "slug", Map.of("type", "string",
                                                "description", "Unique channel slug, e.g. \"design\"."),
                                        "name", Map.of("type", "string",
                                                "description", "Display name for the channel."),
                                        "visibility", Map.of("type", "string",
                                                "enum", List.of("public", "private"),
                                                "description", "Who may see the channel. Defaults to public."),
                                        "agentInstructions", Map.of("type", "string",
                                                "description", "Advisory instructions for agents working in this "
                                                        + "channel. Not enforced.")),
                                "required", List.of("slug", "name"),
                                "additionalProperties", false)));
    }
}
