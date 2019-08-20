package com.jediterm;

import java.io.IOException;

/**
 * @author traff
 */
public class VtEmulatorTest extends EmulatorTestAbstract {
  /**
   * Test of screen features
   */
  
  public void testTest2_Screen_1() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_2() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_3() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_4() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_5() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_6() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_7() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_8() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_9() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_10() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_11() throws IOException {
    doVtTest();
  }
  
  public void testTest2_Screen_12() throws IOException {
    doVtTest();
  }
  
  public void testTest2_Screen_13() throws IOException {
    doVtTest();
  }
  
  public void testTest2_Screen_14() throws IOException {
    doVtTest();
  }
  
  public void testTest2_Screen_15() throws IOException {
    doVtTest();
  }

  /**
   * Test of characters 
   */

  public void testTest3_Characters_1() throws IOException {
    System.setProperty("jediterm.enable.shift_out.character.support", Boolean.TRUE.toString());
    doVtTest();
  }

  private void doVtTest() throws IOException {
    doTest();
  }

  @Override
  protected String getPathToTest() {
    String name = getName().substring(4);
    int ind = name.lastIndexOf("_");
    return TestPathsManager.getTestDataPath() + "vttest/" + name.substring(0, ind) + "/" + name.substring(ind+1);
  }
}
