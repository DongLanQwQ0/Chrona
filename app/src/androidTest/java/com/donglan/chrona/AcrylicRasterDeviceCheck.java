package com.donglan.chrona;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.RenderNode;
import android.os.Bundle;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Isolated native raster measurements; no activities, preferences or schedule records are changed. */
final class AcrylicRasterDeviceCheck {
    static String run(Instrumentation test, Bundle arguments) throws Exception {
        JSONObject[] report = {null}; Throwable[] error = {null};
        test.runOnMainSync(() -> {
            try {
                var context = test.getTargetContext();
                FrameLayout parent = new FrameLayout(context); UiStyle.card(parent);
                DockNavigationLayout dock = new DockNavigationLayout(context, index -> { }, () -> { });
                parent.addView(dock);
                int width = 1080, height = 168;
                dock.layout(0, 0, width, height); dock.getBackground().setBounds(0, 0, width, height);
                RenderNode node = new RenderNode("Chrona acrylic raster fixture");
                Field raster = dock.getBackground().getClass().getDeclaredField("railSurface"); raster.setAccessible(true);
                for (int i = 0; i < 10; i++) draw(dock, node, width, height);
                Bitmap initial = (Bitmap) raster.get(dock.getBackground());
                File image = new File(context.getExternalFilesDir(null), "acrylic-raster-panel.png");
                try (var output = new FileOutputStream(image)) { initial.compress(Bitmap.CompressFormat.PNG, 100, output); }
                int previous = initial.getGenerationId(), changed = 0; long[] nanos = new long[100];
                for (int i = 0; i < nanos.length; i++) {
                    long started = System.nanoTime(); draw(dock, node, width, height); nanos[i] = System.nanoTime() - started;
                    int generation = ((Bitmap) raster.get(dock.getBackground())).getGenerationId();
                    if (generation != previous) changed++; previous = generation;
                }
                Arrays.sort(nanos);
                if ("true".equals(arguments.getString("expect_cache"))) require(changed == 0, "Unchanged nested plate was rasterized again");
                UiStyle.minimumAcrylicMix(dock, 85); draw(dock, node, width, height);
                int mixGeneration = ((Bitmap) raster.get(dock.getBackground())).getGenerationId();
                require(mixGeneration != previous, "Mix change did not redraw the plate");
                dock.getBackground().setAlpha(128); draw(dock, node, width, height);
                int alphaGeneration = ((Bitmap) raster.get(dock.getBackground())).getGenerationId();
                require(alphaGeneration != mixGeneration, "Alpha change did not redraw the plate");
                dock.getBackground().setBounds(0, 0, width - 80, height); draw(dock, node, width, height);
                int sizeGeneration = ((Bitmap) raster.get(dock.getBackground())).getGenerationId();
                require(sizeGeneration != alphaGeneration, "Bounds change did not redraw the plate");
                parent.setBackground(null); draw(dock, node, width, height);
                int outside = ((Bitmap) raster.get(dock.getBackground())).getGenerationId();
                draw(dock, node, width, height);
                require(((Bitmap) raster.get(dock.getBackground())).getGenerationId() != outside,
                        "Independent plate stopped sampling its live backdrop");
                node.discardDisplayList();
                report[0] = new JSONObject().put("frames", nanos.length).put("changedRasters", changed)
                        .put("cpuMedianMs", nanos[50] / 1_000_000d).put("cpuP90Ms", nanos[90] / 1_000_000d)
                        .put("image", image.getAbsolutePath()).put("mixAlphaBoundsAndLiveBackdrop", true)
                        .put("measurement", "native hardware display-list recording and software plate raster; excludes GPU presentation");
            } catch (Throwable failure) { error[0] = failure; }
        });
        if (error[0] != null) throw new IllegalStateException(error[0]);
        return report[0].toString();
    }

    private static void draw(DockNavigationLayout dock, RenderNode node, int width, int height) {
        var canvas = node.beginRecording(width, height);
        try { dock.getBackground().draw(canvas); } finally { node.endRecording(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
