package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.util.concurrent.atomic.AtomicInteger;

/** Independent entry point; the runner owner may call this without changing shared test dispatch. */
final class FirstPresentationDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        FixtureIsolation.require(test.getTargetContext());
        Activity activity=test.startActivitySync(new Intent(test.getTargetContext(),ZjuHomeworkHistoryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FrameLayout[] host={null};View[] body={null};AtomicInteger presented=new AtomicInteger();
        try {
            test.runOnMainSync(()->{
                host[0]=new FrameLayout(activity);body[0]=new View(activity);
                host[0].addView(body[0],new FrameLayout.LayoutParams(300,200));
                ((FrameLayout)activity.findViewById(android.R.id.content)).addView(host[0],new FrameLayout.LayoutParams(-1,-1));
                UiMotion.enterPage(body[0],presented::incrementAndGet);
                if(presented.get()!=0)throw new AssertionError("Marker consumed before the first data frame");
                UiMotion.finishSwap(body[0]);
                if(presented.get()!=0)throw new AssertionError("Canceled first data frame consumed its marker");
            });
            test.waitForIdleSync();SystemClock.sleep(60);
            if(presented.get()!=0)throw new AssertionError("Canceled frame delivered late");
            test.runOnMainSync(()->UiMotion.enterPage(body[0],presented::incrementAndGet));
            long deadline=SystemClock.uptimeMillis()+2500;
            while(presented.get()==0&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);
            if(presented.get()!=1)throw new AssertionError("Drawable attached frame did not confirm exactly once");
            test.runOnMainSync(()->{
                if(!ValueAnimator.areAnimatorsEnabled()&&(body[0].getAlpha()!=1f||body[0].getTranslationY()!=0f))
                    throw new AssertionError("Reduced motion did not keep its direct final state");
                UiMotion.finishSwap(body[0]);
                UiMotion.enterPage(body[0],presented::incrementAndGet);
                host[0].removeView(body[0]);
            });
            test.waitForIdleSync();SystemClock.sleep(60);
            if(presented.get()!=1)throw new AssertionError("Detached data frame consumed its marker");
            test.runOnMainSync(()->{
                host[0].addView(body[0],new FrameLayout.LayoutParams(300,200));
                UiMotion.enterPage(body[0],presented::incrementAndGet);
                UiMotion.swap(body[0],()->{});
            });
            test.waitForIdleSync();SystemClock.sleep(300);
            if(presented.get()!=1)throw new AssertionError("Swap takeover confirmed the superseded first frame");
            return "PASS: native first presentation waits for drawable start; cancel/detach/swap preserve the marker; confirms once; animatorsEnabled="
                    +ValueAnimator.areAnimatorsEnabled();
        } finally {test.runOnMainSync(activity::finish);}
    }
}
