package dev.gambleclient.utils;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import dev.gambleclient.module.modules.client.Phantom;
import dev.gambleclient.module.modules.client.SelfDestruct;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

public final class Utils {
    public static Color getMainColor(final int n, final int n2) {
        final int f = Phantom.redColor.getIntValue();
        final int f2 = Phantom.greenColor.getIntValue();
        final int f3 = Phantom.blueColor.getIntValue();
        if (Phantom.enableRainbowEffect.getValue()) {
            return ColorUtil.a(n2, n);
        }
        if (Phantom.enableBreathingEffect.getValue()) {
            return ColorUtil.alphaStep_Skidded_From_Prestige_Client_NumberOne(new Color(f, f2, f3, n), n2, 20);
        }
        return new Color(f, f2, f3, n);
    }

    public static File getCurrentJarPath() throws URISyntaxException {
        return new File(SelfDestruct.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
    }

    @SuppressWarnings("deprecation")
    public static void overwriteFile(final String spec, final File file) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(spec).openConnection();
            connection.setRequestMethod("GET");
            final InputStream is = connection.getInputStream();
            final FileOutputStream fos = new FileOutputStream(file);
            final byte[] buf = new byte[1024];
            while (true) {
                final int read = is.read(buf);
                if (read == -1) {
                    break;
                }
                fos.write(buf, 0, read);
            }
            fos.close();
            is.close();
            connection.disconnect();
        } catch (Throwable _t) {
            _t.printStackTrace(System.err);
        }
    }

    public static void copyVector(final Vector3d vector3d, final Vec3d vec3d) {
        vector3d.x = vec3d.x;
        vector3d.y = vec3d.y;
        vector3d.z = vec3d.z;
    }
}
