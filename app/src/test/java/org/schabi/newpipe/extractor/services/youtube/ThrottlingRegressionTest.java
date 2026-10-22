package org.schabi.newpipe.extractor.services.youtube;

import org.junit.Test;
import org.schabi.newpipe.extractor.utils.JavaScript;
import static org.junit.Assert.*;

public class ThrottlingRegressionTest {
    @Test public void solvesParameterWithExternalDependenciesAndCurrentPlayerWrapper() throws Exception {
        // Minimal fixture: modern player functions depend on code outside their own body.
        String player = "var _yt_player={};(function(g){var window=this;"
            + "function External(){this.values={};}"
            + "External.prototype.set=function(k,v){this.values[k]=v;};\n"
            + "External.prototype.get=function(k){return this.values[k];};\n"
            + "External.prototype.solve=function(){this.set('n',this.get('n').split('').reverse().join(''));};\n"
            + "var QO;QO=function(a,b,c){var F=new External();F.set(\"alr\",\"yes\");b&&F.set('s',b);return F;};"
            + "})(_yt_player);";
        assertEquals("QO",YoutubeThrottlingParameterUtils.getDeobfuscationFunctionName(player));
        String code=YoutubeThrottlingParameterUtils.getDeobfuscationCode(player);
        assertEquals("321cba",JavaScript.run(code,YoutubeThrottlingParameterUtils.DEOBFUSCATION_FUNCTION_NAME,"abc123"));
    }
    @Test public void cpuBoundJavaScriptCanBeInterrupted() throws Exception {
        java.util.concurrent.CountDownLatch exited = new java.util.concurrent.CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try { JavaScript.run("function spin(){while(true){};}", "spin"); }
            catch (java.util.concurrent.CancellationException expected) { }
            finally { exited.countDown(); }
        });
        worker.setDaemon(true);
        worker.start();
        worker.interrupt();
        assertTrue("Interpreter must respect cancellation", exited.await(3, java.util.concurrent.TimeUnit.SECONDS));
    }
    @Test public void missingNParameterLeavesUrlUnchanged() throws Exception {
        String url="https://example.test/video?itag=137";
        assertEquals(url,YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated("test",url));
    }
}
