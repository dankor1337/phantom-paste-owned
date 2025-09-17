package dev.gambleclient.utils.embed;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

public class DiscordWebhook {
    private final String webhookUrl;
    private String content;
    private String username;
    private String avatarUrl;
    private boolean tts;
    private final List<EmbedObject> embeds;

    public DiscordWebhook(final String webhookUrl) {
        this.embeds = new ArrayList<>();
        this.webhookUrl = webhookUrl;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setAvatarUrl(final String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setTts(final boolean tts) {
        this.tts = tts;
    }

    public void addEmbed(final EmbedObject embed) {
        this.embeds.add(embed);
    }

    @SuppressWarnings("deprecation")
    public void execute() throws Throwable {
        if (this.content == null && this.embeds.isEmpty()) {
            throw new IllegalArgumentException("Set content or add at least one EmbedObject");
        }
        final JSONObject jsonSerializer = new JSONObject();
        jsonSerializer.put("content", this.content);
        jsonSerializer.put("username", this.username);
        jsonSerializer.put("avatar_url", this.avatarUrl);
        jsonSerializer.put("tts", this.tts);
        if (!this.embeds.isEmpty()) {
            final ArrayList<JSONObject> embedList = new ArrayList<>();
            for (EmbedObject embed : this.embeds) {
                JSONObject jsonEmbed = new JSONObject();
                jsonEmbed.put("title", embed.title);
                jsonEmbed.put("description", embed.description);
                jsonEmbed.put("url", embed.url);
                if (embed.color != null) {
                    Color color = embed.color;
                    jsonEmbed.put("color", ((color.getRed() << 8) + color.getGreen() << 8) + color.getBlue());
                }
                Footer footer = embed.footer;
                Image image = embed.image;
                Thumbnail thumbnail = embed.thumbnail;
                Author author = embed.author;
                List<Field> fields = embed.fields;
                if (footer != null) {
                    JSONObject jsonFooter = new JSONObject();
                    jsonFooter.put("text", footer.text);
                    jsonFooter.put("icon_url", footer.iconUrl);
                    jsonEmbed.put("footer", jsonFooter);
                }
                if (image != null) {
                    JSONObject jsonImage = new JSONObject();
                    jsonImage.put("url", image.url);
                    jsonEmbed.put("image", jsonImage);
                }
                if (thumbnail != null) {
                    JSONObject jsonThumbnail = new JSONObject();
                    jsonThumbnail.put("url", thumbnail.url);
                    jsonEmbed.put("thumbnail", jsonThumbnail);
                }
                if (author != null) {
                    JSONObject jsonAuthor = new JSONObject();
                    jsonAuthor.put("name", author.name);
                    jsonAuthor.put("url", author.url);
                    jsonAuthor.put("icon_url", author.iconUrl);
                    jsonEmbed.put("author", jsonAuthor);
                }
                final ArrayList<JSONObject> jsonFields = new ArrayList<>();
                for (Field field : fields) {
                    JSONObject jsonField = new JSONObject();
                    jsonField.put("name", field.name());
                    jsonField.put("value", field.value());
                    jsonField.put("inline", field.inline());
                    jsonFields.add(jsonField);
                }
                jsonEmbed.put("fields", jsonFields.toArray());
                embedList.add(jsonEmbed);

            }
            jsonSerializer.put("embeds", embedList.toArray());
        }
        URLConnection connection = new URL(this.webhookUrl).openConnection();
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "YourLocalLinuxUser");
        connection.setDoOutput(true);
        ((HttpsURLConnection) connection).setRequestMethod("POST");
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(jsonSerializer.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        outputStream.close();
        connection.getInputStream().close();
        ((HttpsURLConnection) connection).disconnect();
    }


    static class JSONObject {
        private final HashMap<String, Object> data;

        JSONObject() {
            this.data = new HashMap<>();
        }

        void put(final String key, final Object value) {
            if (value != null) {
                this.data.put(key, value);
            }
        }

        @Override
        public String toString() {
            final StringBuilder stringBuilder = new StringBuilder();
            final Set<Map.Entry<String, Object>> entrySet = this.data.entrySet();
            stringBuilder.append("{");
            int count = 0;
            for (final Map.Entry<String, Object> entry : entrySet) {
                final Object value = entry.getValue();
                stringBuilder.append(escapeString(entry.getKey())).append(":");
                if (value instanceof String) {
                    stringBuilder.append(escapeString(String.valueOf(value)));
                } else if (value instanceof Integer) {
                    stringBuilder.append(Integer.valueOf(String.valueOf(value)));
                } else if (value instanceof Boolean) {
                    stringBuilder.append(value);
                } else if (value instanceof JSONObject) {
                    stringBuilder.append(value);
                } else if (value.getClass().isArray()) {
                    stringBuilder.append("[");
                    for (int length = Array.getLength(value), i = 0; i < length; ++i) {
                        final StringBuilder append = stringBuilder.append(Array.get(value, i).toString());
                        String separator;
                        if (i != length - 1) {
                            separator = ",";
                        } else {
                            separator = "";
                        }
                        append.append(separator);
                    }
                    stringBuilder.append("]");
                }
                ++count;
                stringBuilder.append(count == entrySet.size() ? "}" : ",");
            }
            return stringBuilder.toString();
        }

        private String escapeString(final String str) {
            return "\"" + str;
        }
    }

    public static class EmbedObject {
        public String title;
        public String description;
        public String url;
        public Color color;
        public Footer footer;
        public Thumbnail thumbnail;
        public Image image;
        public Author author;
        public final List<Field> fields;

        public EmbedObject() {
            this.fields = new ArrayList<>();
        }

        public EmbedObject setDescription(String description) {
            this.description = description;
            return this;
        }

        public EmbedObject setColor(Color color) {
            this.color = color;
            return this;
        }

        public EmbedObject setTitle(String title) {
            this.title = title;
            return this;
        }

        public EmbedObject setUrl(String url) {
            this.url = url;
            return this;
        }

        public EmbedObject setFooter(String text, String iconUrl) {
            this.footer = new Footer(text, iconUrl);
            return this;
        }

        public EmbedObject setImage(Image image) {
            this.image = image;
            return this;
        }

        public EmbedObject setThumbnail(String url) {
            this.thumbnail = new Thumbnail(url);
            return this;
        }

        public EmbedObject setAuthor(Author author) {
            this.author = author;
            return this;
        }

        public EmbedObject addField(final String name, final String value, final boolean inline) {
            this.fields.add(new Field(name, value, inline));
            return this;
        }

    }

    record Image(String url) {
    }

    record Footer(String text, String iconUrl) {
    }

    record Field(String name, String value, boolean inline) {
    }

    record Author(String name, String url, String iconUrl) {
    }

    record Thumbnail(String url) {
    }

}
