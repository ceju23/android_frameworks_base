package com.android.systemui.statusbar.phone;

import java.util.ArrayList;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class PanelBar extends FrameLayout {
    public static final boolean DEBUG = false;
    public static final String TAG = PanelView.class.getSimpleName();
    public static final void LOG(String fmt, Object... args) {
        if (!DEBUG) return;
        Slog.v(TAG, String.format(fmt, args));
    }

    private PanelHolder mPanelHolder;
    private ArrayList<PanelView> mPanels = new ArrayList<PanelView>();
    protected PanelView mTouchingPanel;
    private static final int STATE_CLOSED = 0;
    private static final int STATE_TRANSITIONING = 1;
    private static final int STATE_OPEN = 2;
    private int mState = STATE_CLOSED;
    private boolean mTracking;

    private void go(int state) {
        LOG("go state: %d -> %d", mState, state);
        mState = state;
    }

    public PanelBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void addPanel(PanelView pv) {
        mPanels.add(pv);
        pv.setBar(this);
    }

    public void setPanelHolder(PanelHolder ph) {
        if (ph == null) {
            Slog.e(TAG, "setPanelHolder: null PanelHolder", new Throwable());
            return;
        }
        ph.setBar(this);
        mPanelHolder = ph;
        final int N = ph.getChildCount();
        for (int i=0; i<N; i++) {
            final PanelView v = (PanelView) ph.getChildAt(i);
            if (v != null) {
                addPanel(v);
            }
        }
    }

    public float getBarHeight() {
        return getMeasuredHeight();
    }

    public PanelView selectPanelForTouchX(float x) {
        final int N = mPanels.size();
        return mPanels.get((int)(N * x / getMeasuredWidth()));
    }

    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Allow subclasses to implement enable/disable semantics
        if (!isEnabled()) return false;

        // figure out which panel needs to be talked to here
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchingPanel = selectPanelForTouchX(event.getX());
            mPanelHolder.setSelectedPanel(mTouchingPanel);
            LOG("PanelBar.onTouch: state=%d ACTION_DOWN: panel %s", mState, mTouchingPanel.getName());
            if (mState == STATE_CLOSED || mState == STATE_OPEN) {
                go(STATE_TRANSITIONING);
                onPanelPeeked();
            }
        }
        final boolean result = mTouchingPanel.getHandle().dispatchTouchEvent(event);
        return result;
    }

    public void panelExpansionChanged(PanelView panel, float frac) {
        boolean fullyClosed = true;
        PanelView fullyOpenedPanel = null;
        LOG("panelExpansionChanged: start state=%d panel=%s", mState, panel.getName());
        for (PanelView pv : mPanels) {
            // adjust any other panels that may be partially visible
            if (pv.getExpandedHeight() > 0f) {
                fullyClosed = false;
                final float thisFrac = pv.getExpandedFraction();
                LOG("panelExpansionChanged:  -> %s: f=%.1f", pv.getName(), thisFrac);
                if (panel == pv) {
                    if (thisFrac == 1f) fullyOpenedPanel = panel;
                } else {
                    pv.setExpandedFraction(1f-frac);
                }
            }
        }
        if (fullyOpenedPanel != null && !mTracking) {
            go(STATE_OPEN);
            onPanelFullyOpened(fullyOpenedPanel);
        } else if (fullyClosed && !mTracking) {
            go(STATE_CLOSED);
            onAllPanelsCollapsed();
        }

        LOG("panelExpansionChanged: end state=%d [%s%s ]", mState,
                (fullyOpenedPanel!=null)?" fullyOpened":"", fullyClosed?" fullyClosed":"");
    }

    public void collapseAllPanels(boolean animate) {
        for (PanelView pv : mPanels) {
            if (animate && pv == mTouchingPanel) {
                mTouchingPanel.collapse();
            } else {
                pv.setExpandedFraction(0); // just in case
            }
        }
    }

    public void onPanelPeeked() {
        LOG("onPanelPeeked");
    }

    public void onAllPanelsCollapsed() {
        LOG("onAllPanelsCollapsed");
    }

    public void onPanelFullyOpened(PanelView openPanel) {
        LOG("onPanelFullyOpened");
    }

    public void onTrackingStarted(PanelView panel) {
        mTracking = true;
        if (panel != mTouchingPanel) {
            LOG("shouldn't happen: onTrackingStarted(%s) != mTouchingPanel(%s)",
                    panel, mTouchingPanel);
        }
    }

    public void onTrackingStopped(PanelView panel) {
        mTracking = false;
        panelExpansionChanged(panel, panel.getExpandedFraction());
    }
}