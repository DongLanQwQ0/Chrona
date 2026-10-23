"""Run the production history, commit and Back handlers with Android view stand-ins."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/section-history-check'
activity = (src / 'DashboardActivity.java').read_text(encoding='utf-8')

def method(marker):
    start = activity.index(marker)
    end = activity.index('{', start) + 1
    depth = 1
    while depth:
        depth += (activity[end] == '{') - (activity[end] == '}')
        end += 1
    return activity[start:end]

controller = '\n'.join(method(marker) for marker in (
    'private void completeSectionDrag(', 'private boolean handleDashboardBack('))
fixture = '''package com.donglan.chrona;
import java.util.*;
public class DashboardActivity {
 static final int HOME=0,INBOX=1,SCHEDULE=2;
 final SectionHistory sectionHistory=new SectionHistory();
 int section=HOME,dragDirection,scheduleSource,requests,lastRequest=-1,homeworkFocusRequests;
 boolean sectionSwitchFromHistory,dragTargetLoaded,pageTransitionRunning,sectionLoadRunning,initialDataReady=true,startupReported=true;
 final Set<Long> selection=new HashSet<>();final List<Object>sectionAcrylicSurfaces=new ArrayList<>();
 final Map<Integer,SectionPage>sectionPages=new HashMap<>();
 static class Scroll {float translation;void setTranslationX(float value){translation=value;}}
 class SectionPage {int section;final Scroll scroll=new Scroll();SectionPage(){section=DashboardActivity.this.section;}SectionPage(int value){section=value;}}
 static class Pager {void removeView(Scroll scroll){}}
 static class Dock {void finishPageSelection(int position){}}
 static class Chrome {void setHome(boolean home){}} Chrome dockChrome=new Chrome();
 static class Queue {void removeCallbacks(Runnable action){}void post(Runnable action){}}
 Scroll scroll=new Scroll();SectionPage adjacentPage;final Pager pager=new Pager();final Dock navigation=new Dock();final Queue refresh=new Queue();
 final Runnable deferredSectionRefresh=()->{};
 Set<Long>currentSelection(){return selection;}void clearCurrentSelection(){selection.clear();}
 void activateSectionPage(SectionPage target){section=target.section;scroll=target.scroll;}
 int sectionPosition(int target){return target;}void updateSelectionBack(){}void drawNavigation(){}void invalidateSectionSurfaces(){}
 void refreshSystemCalendar(){}void switchToPendingSection(){}void afterDraw(Scroll scroll,Runnable action){}void maybeReportStartup(){}
 void queueHomeworkFocus(){homeworkFocusRequests++;}
 void switchTo(int destination,boolean fromHistory){requests++;lastRequest=destination;sectionSwitchFromHistory=fromHistory;pageTransitionRunning=true;adjacentPage=new SectionPage(destination);}
 @@CONTROLLER@@
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 void visit(int target){adjacentPage=new SectionPage(target);completeSectionDrag(true);}
 public static void main(String[]args){
  SectionHistory history=new SectionHistory();
  check(history.previous()==-1,"empty history delegates system Back");
  history.record(HOME,HOME);check(history.snapshot().length==0,"same section creates no visit");
  for(int i=0;i<15;i++)history.record(i%3,(i+1)%3);
  check(history.snapshot().length==10&&history.snapshot()[0]==5%3,"history retains latest ten only");
  int[] saved=history.snapshot();SectionHistory restored=new SectionHistory();restored.restore(saved);
  check(Arrays.equals(saved,restored.snapshot()),"state restores visit order");
  for(int i=9;i>=0;i--){check(restored.previous()==saved[i],"Back order");restored.commitBack(saved[i]);}
  check(restored.previous()==-1,"ten returns exhaust history");
  restored.restore(new int[]{-1,HOME,99,SCHEDULE});check(Arrays.equals(restored.snapshot(),new int[]{HOME,SCHEDULE}),"restore ignores invalid section values");
  restored.restore(null);check(restored.previous()==-1,"absent saved history clears state");
  DashboardActivity d=new DashboardActivity();d.visit(SCHEDULE);d.visit(INBOX);
  check(d.homeworkFocusRequests==1,"schedule commit still requests pending homework focus");
  check(Arrays.equals(d.sectionHistory.snapshot(),new int[]{HOME,SCHEDULE}),"real commit records outgoing page once");
  d.adjacentPage=d.new SectionPage(HOME);d.completeSectionDrag(false);
  check(d.section==INBOX&&d.sectionHistory.previous()==SCHEDULE,"cancelled preview does not add history");
  d.selection.add(1L);check(d.handleDashboardBack()&&d.selection.isEmpty()&&d.requests==0,"multi-selection consumes Back before history");
  check(d.handleDashboardBack()&&d.lastRequest==SCHEDULE&&d.sectionHistory.previous()==SCHEDULE,"Back requests previous page without early pop");
  check(d.handleDashboardBack()&&d.requests==1,"repeated Back during animation does not skip pages");
  d.completeSectionDrag(true);check(d.section==SCHEDULE&&Arrays.equals(d.sectionHistory.snapshot(),new int[]{HOME}),"successful Back pops instead of pushing");
  d.handleDashboardBack();d.completeSectionDrag(false);check(d.sectionHistory.previous()==HOME,"cancelled Back preserves entry");
  d.sectionLoadRunning=true;int requests=d.requests;check(d.handleDashboardBack()&&d.requests==requests,"repeated Back during loading is ignored");d.sectionLoadRunning=false;
  d.handleDashboardBack();d.completeSectionDrag(true);check(d.section==HOME&&!d.handleDashboardBack(),"last Back restores first page then releases to system");
  d.sectionHistory.record(HOME,SCHEDULE);d.sectionSwitchFromHistory=true;d.completeSectionDrag(true);check(d.sectionHistory.previous()==HOME,"missing commit target never consumes history");
  System.out.println("Section history checks passed: "+checks+"; production history, commit and Back handlers");
 }
}'''.replace('@@CONTROLLER@@', controller)
path = out / 'com/donglan/chrona/DashboardActivity.java'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(fixture, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out),
                str(path), str(src / 'SectionHistory.java')], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.DashboardActivity'], check=True)

assert 'sectionHistory.restore(state.getIntArray("section_history"))' in activity
assert 'state.putIntArray("section_history", sectionHistory.snapshot())' in activity
assert '|| sectionHistory.previous() >= 0' in method('private void updateSelectionBack(')
assert 'this::handleDashboardBack' in method('private void updateSelectionBack(')
assert 'if (!handleDashboardBack()) super.onBackPressed()' in method('@Override public void onBackPressed(')
assert 'sectionHistory.' not in method('private void activateSectionPage(')
