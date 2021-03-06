/*
 * Copyright (c) 2006 Stephan D. Cote' - All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the MIT License which accompanies this distribution, and is
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote
 *      - Initial concept and implementation
 */
package coyote.i13n;

/**
 * The NullMaster class models...
 */
public class NullMaster implements TimerMaster {

  /**
   * @return The name of this timer set.
   */
  @Override
  public String getName() {
    return "";
  }




  @Override
  public void increase(final long value) {
    // no-op implementation
  }




  @Override
  public void start(final Timer mon) {
    // no-op implementation
  }




  @Override
  public void stop(final Timer mon) {
    // no-op implementation
  }

}
