package com.donglan.chrona;

import com.donglan.chrona.net.RequestControl;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Runs the actual admission gate and RequestControl; no Android, provider or network doubles. */
public final class ClassroomSummarySlotsCheck {
    private static int checks;
    private static final ExecutorService workers=Executors.newCachedThreadPool();
    private static void check(boolean yes,String name){checks++;if(!yes)throw new AssertionError(name);}
    private static void await(BooleanSupplier condition)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(!condition.getAsBoolean()){
            if(System.nanoTime()>until)throw new AssertionError("Timed out waiting for gate state");
            Thread.sleep(5);
        }
    }
    private static Future<ClassroomSummarySlots.Lease> enter(ClassroomSummarySlots gate,AtomicInteger limit,AtomicInteger requests){
        return workers.submit(()->{try(RequestControl control=new RequestControl(5000)){
            var slot=gate.acquire(control,limit::get,()->{});requests.incrementAndGet();return slot;
        }});
    }
    public static void main(String[] args)throws Exception{
        try{
            ClassroomSummarySlots gate=new ClassroomSummarySlots();AtomicInteger limit=new AtomicInteger(3),requests=new AtomicInteger();
            var first=enter(gate,limit,requests).get(2,TimeUnit.SECONDS);
            var second=enter(gate,limit,requests).get(2,TimeUnit.SECONDS);
            var third=enter(gate,limit,requests).get(2,TimeUnit.SECONDS);
            check(gate.activeCount()==3&&requests.get()==3,"three requests admitted simultaneously");
            var fourth=enter(gate,limit,requests);await(()->gate.queuedCount()==1);
            var fifth=enter(gate,limit,requests);await(()->gate.queuedCount()==2);
            check(!fourth.isDone()&&!fifth.isDone()&&requests.get()==3,"fourth and fifth wait without requesting");
            second.close();var fourthSlot=fourth.get(2,TimeUnit.SECONDS);
            check(gate.activeCount()==3&&requests.get()==4&&!fifth.isDone(),"release fills FIFO head without admitting later waiter");
            third.close();var fifthSlot=fifth.get(2,TimeUnit.SECONDS);
            first.close();fourthSlot.close();fifthSlot.close();first.close();
            check(gate.activeCount()==0&&gate.queuedCount()==0,"leases release once and queue drains");

            for(int selected:new int[]{1,2}){
                limit.set(selected);var slots=new java.util.ArrayList<ClassroomSummarySlots.Lease>();
                for(int i=0;i<selected;i++)slots.add(enter(gate,limit,requests).get(2,TimeUnit.SECONDS));
                int before=requests.get();var waiting=enter(gate,limit,requests);await(()->gate.queuedCount()==1);
                check(gate.activeCount()==selected&&!waiting.isDone()&&requests.get()==before,"selected limit "+selected+" blocks next request");
                slots.remove(0).close();var replacement=waiting.get(2,TimeUnit.SECONDS);
                check(gate.activeCount()==selected,"selected limit fills released slot "+selected);
                for(var slot:slots)slot.close();replacement.close();
            }

            limit.set(3);var a=enter(gate,limit,requests).get();var b=enter(gate,limit,requests).get();var c=enter(gate,limit,requests).get();
            var reduced=enter(gate,limit,requests);await(()->gate.queuedCount()==1);limit.set(1);
            a.close();b.close();await(()->gate.activeCount()==1);
            check(!reduced.isDone(),"reducing saved limit preserves active request and prevents new entry");
            c.close();var reducedSlot=reduced.get(2,TimeUnit.SECONDS);check(gate.activeCount()==1,"waiting queue observes reduced limit");
            var raised=enter(gate,limit,requests);await(()->gate.queuedCount()==1);limit.set(2);
            var raisedSlot=raised.get(2,TimeUnit.SECONDS);check(gate.activeCount()==2,"waiting queue observes increased limit without new submission");
            reducedSlot.close();raisedSlot.close();

            limit.set(1);var held=enter(gate,limit,requests).get();int before=requests.get();AtomicInteger queued=new AtomicInteger();
            try(RequestControl cancelled=new RequestControl(5000)){
                Future<?> stopped=workers.submit(()->{try(var slot=gate.acquire(cancelled,limit::get,queued::incrementAndGet)){
                    requests.incrementAndGet();throw new AssertionError("cancelled waiter requested");
                }catch(IOException expected){return;}});
                await(()->gate.queuedCount()==1&&queued.get()==1);cancelled.cancel();stopped.get(2,TimeUnit.SECONDS);
                check(gate.queuedCount()==0&&requests.get()==before&&gate.activeCount()==1,"waiting cancellation removes ticket without request or slot leak");
            }
            try(RequestControl expired=new RequestControl(80)){
                Future<?> timed=workers.submit(()->{try(var slot=gate.acquire(expired,limit::get,()->{})){
                    requests.incrementAndGet();throw new AssertionError("expired waiter requested");
                }catch(SocketTimeoutException expected){return;}catch(IOException other){throw new AssertionError(other);}});
                timed.get(2,TimeUnit.SECONDS);check(gate.queuedCount()==0&&requests.get()==before,"waiting total deadline expires without request");
            }
            try(RequestControl cancelled=new RequestControl(5000)){
                cancelled.cancel();try{gate.acquire(cancelled,limit::get,()->{});throw new AssertionError("pre-cancel admitted");}catch(IOException expected){checks++;}
            }
            AtomicInteger interruptedResult=new AtomicInteger();
            Thread interrupted=new Thread(()->{
                try(RequestControl control=new RequestControl(5000);var slot=gate.acquire(control,limit::get,()->{})){
                    requests.incrementAndGet();interruptedResult.set(-1);
                }catch(IOException expected){interruptedResult.set(Thread.currentThread().isInterrupted()?1:-2);}
            });
            interrupted.start();await(()->gate.queuedCount()==1);interrupted.interrupt();interrupted.join(2000);
            check(!interrupted.isAlive()&&interruptedResult.get()==1&&gate.queuedCount()==0&&requests.get()==before,
                    "thread interruption removes waiter, preserves interrupt and starts no request");
            try(RequestControl control=new RequestControl(5000)){
                try{gate.acquire(control,limit::get,()->{throw new IllegalStateException("progress failure");});throw new AssertionError("progress exception lost");}
                catch(IllegalStateException expected){check(gate.queuedCount()==0,"queue callback exception removes ticket");}
            }
            held.close();
            try(RequestControl control=new RequestControl(5000)){
                try(var slot=gate.acquire(control,limit::get,()->{})){throw new IOException("synthetic generator failure");}
                catch(IOException expected){check(gate.activeCount()==0,"generator exception releases lease in finally");}
                try(var slot=gate.acquire(control,limit::get,()->{})){check(gate.activeCount()==1,"next request enters after generator exception");}
            }
            check(gate.activeCount()==0&&gate.queuedCount()==0,"all tests leave gate empty");
            System.out.println("ClassroomSummarySlotsCheck PASS: "+checks+" checks; 3/2/1 limits, FIFO fill, live limits, cancellation, total timeout and exception cleanup");
        }finally{workers.shutdownNow();workers.awaitTermination(5,TimeUnit.SECONDS);}
    }
}
