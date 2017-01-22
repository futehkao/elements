/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * NOTE.  This file is adapted from java CRaSH.
 */
package crash.commands.base

import org.crsh.cli.Command
import org.crsh.cli.Usage

import java.lang.management.ManagementFactory

import org.crsh.command.InvocationContext
import org.crsh.cli.Argument
import org.crsh.command.Pipe
import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryUsage;

@Usage("JVM information")
class jvm {

    /**
     * Show JMX data about os.
     */
    @Usage("Show JVM operating system")
    @Command
    public void system(InvocationContext<Map> context) {
        def os = ManagementFactory.operatingSystemMXBean;
        context.provide([name: "architecture", value: os?.arch]);
        context.provide([name: "name", value: os?.name]);
        context.provide([name: "version", value: os?.version]);
        context.provide([name: "processors", value: os?.availableProcessors]);
    }

    /**
     * Show JMX data about Runtime.
     */
    @Usage("Show JVM runtime")
    @Command
    public void runtime() {
        def rt = ManagementFactory.runtimeMXBean
        context.provide([name: "name", value: rt?.name]);
        context.provide([name: "specName", value: rt?.specName]);
        context.provide([name: "specVendor", value: rt?.specVendor]);
        context.provide([name: "managementSpecVersion", value: rt?.managementSpecVersion]);
    }

    /**
     * Show JMX data about Class Loading System.
     */
    @Usage("Show JVM classloding")
    @Command
    public void classloading() {
        def cl = ManagementFactory.classLoadingMXBean
        context.provide([name: "isVerbose ", value: cl?.isVerbose()]);
        context.provide([name: "loadedClassCount ", value: cl?.loadedClassCount]);
        context.provide([name: "totalLoadedClassCount ", value: cl?.totalLoadedClassCount]);
        context.provide([name: "unloadedClassCount ", value: cl?.unloadedClassCount]);
    }

    /**
     * Show JMX data about Compilation.
     */
    @Usage("Show JVM compilation")
    @Command
    public void compilation() {
        def comp = ManagementFactory.compilationMXBean
        context.provide([name: "totalCompilationTime ", value: comp.totalCompilationTime]);
    }

    /**
     * Show memory heap.
     */
    @Usage("Show JVM memory heap")
    @Command
    public void heap(InvocationContext<MemoryUsage> context) {
        def mem = ManagementFactory.memoryMXBean
        def heap = mem.heapMemoryUsage
        // context.provide(heap);
        context.provide([name:"commited ",value:heap?.committed]);
        context.provide([name:"init ",value:heap?.init]);
        context.provide([name:"max ",value:heap?.max]);
        context.provide([name:"used ",value:heap?.used]);
    }

    /**
     * Show memory non heap.
     */
    @Usage("Show JVM memory non heap")
    @Command
    public void nonheap(InvocationContext<MemoryUsage> context) {
        def mem = ManagementFactory.memoryMXBean
        def nonHeap = mem.nonHeapMemoryUsage
        context.provide(nonHeap);
//    context.provide([name:"commited ",value:nonHeap?.committed]);
//    context.provide([name:"init ",value:nonHeap?.init]);
//    context.provide([name:"max ",value:nonHeap?.max]);
//    context.provide([name:"used ",value:nonHeap?.used]);
    }

    /**
     * Show JMX data about Memory.
     */
    @Usage("Show JVM memory pools")
    @Command
    public void pools(InvocationContext<String> context) {
        ManagementFactory.memoryPoolMXBeans.each { pool ->
            context.provide(pool.name);
        }
    }

    @Command
    public void top(InvocationContext<MemoryUsage> context) {
        while (!Thread.currentThread().interrupted()) {
            out.cls();
            heap(context);
            out.flush();
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Show JMX data about Memory.
     */
    @Usage("Show JVM memory pool")
    @Command
    public Pipe<String, MemoryUsage> pool(@Argument List<String> pools) {
        def mem = ManagementFactory.memoryPoolMXBeans
        return new Pipe<String, MemoryUsage>() {
            @Override
            void open() {
                for (String pool : pools) {
                    provide(pool);
                }
            }

            @Override
            void provide(String element) {

                MemoryPoolMXBean found = null;
                for (MemoryPoolMXBean pool : mem) {
                    if (pool.getName().equals(element)) {
                        found = pool;
                        break;
                    }
                }

                //
                if (found != null) {
//          context.provide(found.peakUsage)
                    context.provide(found.usage)
                }
            }
        }

/*
    def nonHeapUsage = mem.nonHeapMemoryUsage

    out << "\nNON-HEAP STORAGE\n\n";
    out << "committed:" + nonHeapUsage?.committed + "\n";
    out << "init:" + nonHeapUsage?.init + "\n";
    out << "max:" + nonHeapUsage?.max + "\n\n";
*/

/*
    ManagementFactory.memoryPoolMXBeans.each{ mp ->
      out << "name :" + mp?.name + "\n";
      String[] mmnames = mp.memoryManagerNames
      mmnames.each{ mmname ->
              context.provide([name:"Manager Name",value:mmname]);
      }
      context.provide([name:"Type ",value:mp?.type]);
      context.provide([name:"Usage threshold supported ",value:mp?.isUsageThresholdSupported()]);
      out << "\n";
    }
*/
    }

    /**
     * Show JMX data about Thread.
     */
    @Usage("Show JVM garbage collection")
    @Command
    public void gc() {

        out << "\nGARBAGE COLLECTION\n";
        ManagementFactory.garbageCollectorMXBeans.each { gc ->
            out << "name :" + gc?.name + "\n";
            context.provide([name: "collection count ", value: gc?.collectionCount]);
            context.provide([name: "collection time ", value: gc?.collectionTime]);


            String[] mpoolNames = gc.memoryPoolNames
            mpoolNames.each { mpoolName ->
                context.provide([name: "mpool name ", value: mpoolName]);
            }
            out << "\n\n";
        }
    }
}