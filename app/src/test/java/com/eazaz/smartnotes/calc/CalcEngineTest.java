package com.eazaz.smartnotes.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.eazaz.smartnotes.calc.CalcEngine.Config;
import com.eazaz.smartnotes.calc.CalcEngine.Mode;
import com.eazaz.smartnotes.calc.CalcEngine.Output;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class CalcEngineTest {

    private static final Set<String> NONE = Collections.emptySet();

    private static String auto(String t) {
        return CalcEngine.process(t, t.length(), false, Mode.AUTO, Config.defaults(), NONE).text;
    }

    private static String manual(String t) {
        return CalcEngine.process(t, t.length(), false, Mode.MANUAL, Config.defaults(), NONE).text;
    }

    @Test public void lineMath() {
        assertEquals("budgets 10+10+(5*2) = 30", auto("budgets 10+10+(5*2) ="));
        assertEquals("budgets 10+10+(5*3) = 35", auto("budgets 10+10+(5*3) = 30"));
        assertEquals("rice 50 * 2= 100", auto("rice 50 * 2="));
        assertEquals("Rent 5,000 + 2,500 = 7500", auto("Rent 5,000 + 2,500 ="));
        assertEquals("200 + 10% = 220", auto("200 + 10% ="));
        assertEquals("box 5x2 = 10", auto("box 5x2 ="));
        assertEquals("১০+৫ = ১৫", auto("১০+৫ ="));
    }

    @Test public void wordsAndTaka() {
        assertEquals("egg 10 + beef 10 + chicken 10 = 30", auto("egg 10 + beef 10 + chicken 10 =?"));
        assertEquals("rice 50*2 taka = 100", auto("rice 50*2 taka ="));
        assertEquals("rice 50*2 = 100 tk", auto("rice 50*2 = 90 tk"));
        assertEquals("2025 budget 10+5 = 15", auto("2025 budget 10+5 ="));
    }

    @Test public void leavesTextAlone() {
        assertEquals("Total = 30", auto("Total = 30"));
        assertEquals("10+10 = twenty", auto("10+10 = twenty"));
        assertEquals("10/0 =", auto("10/0 ="));
        assertEquals("egg 10 + beef =", auto("egg 10 + beef ="));
    }

    @Test public void units() {
        assertEquals("5 km to m = 5000 m", auto("5 km to m ="));
        assertEquals("100 c to f = 212 f", auto("100 c to f ="));
        assertEquals("1 bigha to katha = 20 katha", auto("1 bigha to katha ="));
    }

    @Test public void groups() {
        assertEquals("Egg 10\nbeef 10\nchicken 10\ntotal = 10 + 10 + 10 = 30",
                auto("Egg 10\nbeef 10\nchicken 10\ntotal ="));
        assertEquals("My budget 30\nBazar 1:\nEgg 10\nbeef 10\ntotal = 10 + 10 = 20\nremaining = 30 - 20 = 10",
                auto("My budget 30\nBazar 1:\nEgg 10\nbeef 10\ntotal =\nremaining ="));
        assertEquals("Bazar 1:\nEgg 10\nbeef 10\ntotal = 10 + 10 = 20\n\nMy budget 1400\n\nUtility 1:\nGas 500\nNet 800\ntotal = 500 + 800 = 1300",
                auto("Bazar 1:\nEgg 10\nbeef 10\ntotal = 20\n\nMy budget 1400\n\nUtility 1:\nGas 500\nNet 800\ntotal = 1300"));
        assertEquals("Budget = 500\nrice 100\nremaining = 500 - 100 = 400", auto("Budget = 500\nrice 100\nremaining ="));
    }

    @Test public void exclusion() {
        String t = "Egg 10\nbeef 10\nchicken 10\ntotal =";
        Output o = CalcEngine.process(t, 0, false, Mode.AUTO, Config.defaults(), NONE);
        assertEquals(3, o.highlights.size());
        Set<String> ex = new HashSet<>();
        ex.add(o.highlights.get(1).key);
        assertEquals("Egg 10\nbeef 10\nchicken 10\ntotal = 10 + 10 = 20",
                CalcEngine.process(o.text, 0, false, Mode.AUTO, Config.defaults(), ex).text);
    }

    @Test public void customWords() {
        Map<String, Character> w = Config.defaultWords();
        w.put("profit", '-');
        String t = "sell 100\ncost 40\nprofit =";
        assertEquals("sell 100\ncost 40\nprofit = 100 - 40 = 60",
                CalcEngine.process(t, 0, false, Mode.AUTO, new Config(w, '+'), NONE).text);
    }

    @Test public void manualMode() {
        String t = "My budget 30\nBazar 1:\nEgg 10\nbeef 10\ntotal =";
        assertEquals(t, manual(t));
        assertTrue(CalcEngine.isManualStart(t, 4));
        Output a = CalcEngine.manualAppend(CalcEngine.manualAppend(t, 4, "10").text, 4, "10");
        assertEquals("My budget 30\nBazar 1:\nEgg 10\nbeef 10\ntotal = 10 + 10", a.text);
        assertEquals("My budget 30\nBazar 1:\nEgg 10\nbeef 10\ntotal = 10 + 10 = 20", manual(a.text + " ="));
    }
}
