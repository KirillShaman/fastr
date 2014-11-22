/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 * 
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestrGenBuiltinConj extends TestBase {

    @Test
    @Ignore
    public void testConj1() {
        assertEval("argv <- list(NA_complex_);Conj(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testConj2() {
        assertEval("argv <- list(c(-1.18540307978262+0i, 1.21560120163195-1.53371687180917i, 2.77616253887119+2.49241439707552i, -0.34590612779948+5.91601304866405i, -4.79620377219434-5.1021793804514i, -0.76948538129694-3.75787028288235i, 7.35246399396265+3.06008718716048i, 3.29255418488526-1.70891939683671i, -4.06380659430245+3.06999922353505i, -3.09223641978001-1.96417605896538i, -0.92141308753853+1.40901240924205i, -2.00249720671212-2.68610936520658i, -0.36243789137685+2.82396143864819i, 0.970540333825845-0.827296527575657i, -0.71012351273056-1.58808368514905i, 0.65264999887605-1.47950735242131i, 2.37634963276553+0.56734038764123i, 1.07643410940103-0.27130380644466i, -0.75915222215558-1.26274988364837i, 0.27719717365392+1.892240358725i, -0.486365810527362+0.32331047458147i, 0.458815916572034+0.775988009981045i, -1.62795265860628+1.25968253919881i, -0.31369767965175+2.67392540646143i, 1.35480053490252+0i, -0.31369767965175-2.67392540646143i, -1.62795265860628-1.25968253919881i, 0.458815916572035-0.775988009981044i, -0.486365810527364-0.323310474581469i, 0.27719717365392-1.892240358725i, -0.75915222215558+1.26274988364837i, 1.07643410940103+0.27130380644466i, 2.37634963276553-0.56734038764123i, 0.65264999887605+1.47950735242131i, -0.71012351273056+1.58808368514905i, 0.970540333825845+0.827296527575658i, -0.36243789137685-2.82396143864819i, -2.00249720671212+2.68610936520658i, -0.92141308753853-1.40901240924205i, -3.09223641978001+1.96417605896538i, -4.06380659430245-3.06999922353505i, 3.29255418488526+1.70891939683671i, 7.35246399396266-3.06008718716047i, -0.76948538129694+3.75787028288235i, -4.79620377219434+5.10217938045139i, -0.34590612779948-5.91601304866405i, 2.77616253887119-2.49241439707552i, 1.21560120163195+1.53371687180917i));Conj(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testConj3() {
        assertEval("argv <- list(structure(c(0.087699990825051-0.22507396943778i, 0.543948655831334+0.33174688242063i, 0.162724647304311-0.15004931295852i, 0.433366361691124+0.22116458828042i, 0.237749303783571-0.07502465647926i, 0.322784067550914+0.11058229414021i, 0.312773960262831+0i, 0.212201773410704-0i, 0.387798616742091+0.07502465647926i, 0.101619479270494-0.11058229414021i, 0.462823273221351+0.15004931295852i, -0.008962814869716-0.22116458828042i, 0.537847929700611+0.22507396943778i, -0.119545109009926-0.33174688242063i), .Dim = c(2L, 7L)));Conj(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testConj4() {
        assertEval("argv <- list(c(1+0i, 0.985449458355365-0.138495889434283i, 0.942642872008266-0.270298493966801i, 0.874055380411015-0.389154527907249i, 0.783616834775482-0.489658143691394i, 0.676434265976222-0.567595743096322i, 0.558433187362516-0.620202886580765i, 0.435944803381395-0.646314749919218i, 0.315270204563124-0.646399711551264i, 0.202254248593737-0.622474571220695i, 0.101900933636988-0.577908912337521i, 0.018058735786294-0.517134531945579i, -0.046801131817278-0.445283024979697i, -0.091697846014566-0.367779972441526i, -0.117138246792619-0.289927334668645i, -0.125-0.21650635094611i, -0.118311211562746-0.151431445234362i, -0.1009450259937-0.097481478474725i, -0.0772542485937368-0.0561284970724482i, -0.0516755705617768-0.027476388254185i, -0.0283351996132097-0.0103131692411995i, -0.0106874359562526-0.0022716846399295i, -1.21500794451954e-03-8.496163204619e-05i, -1.21500794451956e-03+8.49616320463e-05i, -0.0106874359562525+0.0022716846399297i, -0.0283351996132096+0.0103131692411996i, -0.0516755705617767+0.0274763882541851i, -0.0772542485937367+0.0561284970724481i, -0.1009450259937+0.097481478474725i, -0.118311211562746+0.151431445234362i, -0.125+0.21650635094611i, -0.117138246792619+0.289927334668644i, -0.091697846014566+0.367779972441526i, -0.046801131817278+0.445283024979697i, 0.018058735786294+0.517134531945579i, 0.101900933636988+0.577908912337521i, 0.202254248593737+0.622474571220695i, 0.315270204563124+0.646399711551264i, 0.435944803381395+0.646314749919218i, 0.558433187362516+0.620202886580765i, 0.676434265976221+0.567595743096322i, 0.783616834775482+0.489658143691394i, 0.874055380411015+0.389154527907249i, 0.942642872008266+0.270298493966801i, 0.985449458355365+0.138495889434283i));Conj(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testConj5() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L)));Conj(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testConj6() {
        assertEval("argv <- list(FALSE);Conj(argv[[1]]);");
    }
}

