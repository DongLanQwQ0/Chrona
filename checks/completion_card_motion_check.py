"""Execute the real keyed reconciler with small Android View transport doubles."""
from pathlib import Path
import os
import re
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/completion-card-motion-check'
files = {
    'android/view/View.java': '''package android.view;
public class View {
 ViewGroup parent; Object params; int visibility;
 public Object getParent(){return parent;}
 public Object getLayoutParams(){return params;}
 public void setLayoutParams(Object p){params=p;}
 public int getVisibility(){return visibility;}
 public void setVisibility(int v){visibility=v;}
}''',
    'android/view/ViewGroup.java': '''package android.view;
import java.util.*;
public class ViewGroup extends View {
 public final List<View> children=new ArrayList<>(); public int removes;
 public int getChildCount(){return children.size();} public View getChildAt(int i){return children.get(i);}
 public void removeViewAt(int i){View v=children.remove(i);v.parent=null;removes++;}
 public void removeView(View v){removeViewAt(children.indexOf(v));}
 public void addView(View v,int i){if(v.parent!=null)throw new AssertionError("double parent");children.add(i,v);v.parent=this;}
 public void addView(View v){addView(v,children.size());}
}''',
    'com/donglan/chrona/CompletionCardMotionCheck.java': '''package com.donglan.chrona;
import android.view.*;
public class CompletionCardMotionCheck {
 static int count;static void check(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
 static final class Bound extends ViewGroup implements DashboardCardReconciler.BoundBranch {
  int count;
  Bound(int count){this.count=count;addView(new View());}
  public int bindFrom(View incoming){count=((Bound)incoming).count;return 1;}
 }
 static ViewGroup row(DashboardCardReconciler r,String id,String state){ViewGroup v=new ViewGroup();v.addView(new View());r.row(v,id,state);return v;}
 static ViewGroup branch(DashboardCardReconciler r,String id){ViewGroup v=new ViewGroup();r.branch(v,id);return v;}
 public static void main(String[]args){
  DashboardCardReconciler r=new DashboardCardReconciler();ViewGroup target=new ViewGroup();View header=new View();target.addView(header);
  ViewGroup summary=branch(r,"summary"),details=branch(r,"details");summary.addView(details);target.addView(summary);
  ViewGroup a=row(r,"a","open"),b=row(r,"b","open"),c=row(r,"c","open");details.addView(a);details.addView(b);details.addView(c);
  View bContent=b.getChildAt(0);ViewGroup incoming=new ViewGroup(),nextSummary=branch(r,"summary"),nextDetails=branch(r,"details");
  nextSummary.addView(nextDetails);incoming.addView(nextSummary);nextDetails.addView(row(r,"b","open"));nextDetails.addView(row(r,"c","done"));
  r.apply(target,incoming,1);
  check(target.getChildAt(0)==header,"header identity");check(target.getChildAt(1)==summary&&summary.getChildAt(0)==details,"structural identity");
  check(details.getChildCount()==2&&details.getChildAt(0)==b&&details.getChildAt(1)==c,"removed row and surviving card identity");
  check(b.getChildAt(0)==bContent,"unchanged row child identity and listeners");check(c.getChildCount()==1,"changed row replaces only contents");
  check(a.getParent()==null&&b.getParent()==details,"removed row detached; survivor parent stable");
  check(details.removes==1,"removal does not detach surviving rows");
  ViewGroup second=new ViewGroup();second.addView(row(r,"c","done"));second.addView(row(r,"b","open"));second.addView(row(r,"d","open"));
  r.apply(details,second,0);check(details.getChildAt(0)==c&&details.getChildAt(1)==b&&details.getChildCount()==3,"rapid successive snapshot reorders and inserts");
  ViewGroup empty=new ViewGroup();r.apply(details,empty,0);check(details.getChildCount()==0,"last row removal");
  ViewGroup dashboard=new ViewGroup(), refresh=new ViewGroup();Bound live=new Bound(2), updated=new Bound(1);
  View fixed=live.getChildAt(0), retained=row(r,"focus:b","open");
  live.addView(row(r,"focus:a","open"));live.addView(retained);updated.addView(row(r,"focus:b","open"));
  r.branch(live,"todos");r.branch(updated,"todos");dashboard.addView(live);refresh.addView(updated);
  var targeted=new java.util.ArrayList<String>();r.forRows(dashboard,(view,key)->targeted.add(key));
  check(targeted.equals(java.util.List.of("focus:a","focus:b")),"exit targets contain only keyed rows");
  r.apply(dashboard,refresh,0);
  check(live.count==1&&live.getChildAt(0)==fixed&&live.getChildAt(1)==retained,"bound controls update without being replaced");
  System.out.println("Completion card motion checks passed: "+count+"; real reconciler, platform View stand-ins");
 }
}'''
}
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out),
                *map(str, paths), str(src / 'DashboardCardReconciler.java')], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.CompletionCardMotionCheck'], check=True)

activity = (src / 'DashboardActivity.java').read_text(encoding='utf-8')
assert 'disclosure.setRotation(' not in activity
assert 'disclosureIcon.setLevel(' in activity
assert 'completingCandidates.add(candidate.id)' in activity
assert 'completionRefreshRunning || completionActionRunning()' in activity
assert 'return !completingCandidates.isEmpty() || !confirmingActivities.isEmpty();' in activity
assert activity.index('confirmingActivities.add(key)') < activity.index('confirmed.reward(false,')
assert activity.index('confirmingActivities.remove(key)') < activity.index('confirmed.setEnabled(true)')
assert 'TransitionManager.beginDelayedTransition(target, motion)' in activity
assert 'new android.transition.Fade(android.transition.Fade.OUT)' in activity
assert 'homeTodoExpanding()' in activity
assert 'awaitingCompletionRows.put(candidate.id, completionKey(candidate, null))' in activity
assert 'departing.add("upcoming:" + occurrence)' in activity and 'departing.add("timeline:" + occurrence)' in activity
assert 'onTransitionEnd(android.transition.Transition transition)' in activity
assert 'UiMotion.isEnabled() && target.isAttachedToWindow() && target.isLaidOut()' in activity
assert 'completionRows.apply(target, incoming, destination == HOME ? headerChildCount : 0)' in activity
assert 'viewport.scrollTo(0, y)' in activity
apply_rows = activity.split('private void applyCompletionRows(', 1)[1].split('private void collectHomeStrips', 1)[0]
assert apply_rows.index('UiMotion.remember(') < apply_rows.index('completionRows.apply(')
assert 'UiMotion.settleScroll' not in apply_rows and 'UiMotion.enterList' not in apply_rows
assert 'new LinearLayout.LayoutParams(dp(48), dp(48))' in activity
# 收件类型路由随实现同步：源码为 EventCategory.VALUES[categoryIndex-1]，按正则容忍空格。
assert re.search(r'EventCategory\.VALUES\[\s*categoryIndex\s*-\s*1\s*\]', activity)
home_refresh = activity.split('private void refreshHomeContent()', 1)[1].split('private String calendarWindowKey', 1)[0]
assert 'refreshCompletionRows();' in home_refresh and 'render();' not in home_refresh
active_refresh = activity.split('private void refreshActivePageIfNeeded(String version)', 1)[1].split('private ', 1)[0]
assert re.search(r'if \(section == HOME[^\n]*\) \{\s+refreshCompletionRows\(\);\s+return;', active_refresh)
print('Dashboard source contracts passed: icon-only rotation, write lock, refresh gate, reduced motion, scroll retention, homework route, home poll/calendar reconciliation')
