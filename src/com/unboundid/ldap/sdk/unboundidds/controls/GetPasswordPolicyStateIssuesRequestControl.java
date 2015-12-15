/*
 * Copyright 2015 UnboundID Corp.
 * All Rights Reserved.
 */
/*
 * Copyright (C) 2015 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */
package com.unboundid.ldap.sdk.unboundidds.controls;



import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.NotMutable;
import com.unboundid.util.ThreadSafety;
import com.unboundid.util.ThreadSafetyLevel;

import static com.unboundid.ldap.sdk.unboundidds.controls.ControlMessages.*;



/**
 * <BLOCKQUOTE>
 *   <B>NOTE:</B>  This class is part of the Commercial Edition of the UnboundID
 *   LDAP SDK for Java.  It is not available for use in applications that
 *   include only the Standard Edition of the LDAP SDK, and is not supported for
 *   use in conjunction with non-UnboundID products.
 * </BLOCKQUOTE>
 * This class provides an implementation of a request control that can be
 * included in a bind request to indicate that the server should include a
 * control in the bind response with information about any password policy state
 * notices, warnings, and/or errors for the user.
 * <BR><BR>
 * This control has an OID of 1.3.6.1.4.1.30221.2.5.46 and no value.  It must
 * only be used in a bind request that also includes the
 * {@link RetainIdentityRequestControl}, and the authentication identify of the
 * connection prior to sending the bind request must have the
 * permit-get-password-policy-state-issues privilege.
 */
@NotMutable()
@ThreadSafety(level=ThreadSafetyLevel.COMPLETELY_THREADSAFE)
public final class GetPasswordPolicyStateIssuesRequestControl
       extends Control
{
  /**
   * The OID (1.3.6.1.4.1.30221.2.5.46) for the get password policy state issues
   * request control.
   */
  public static final  String GET_PASSWORD_POLICY_STATE_ISSUES_REQUEST_OID =
       "1.3.6.1.4.1.30221.2.5.46";



  /**
   * The serial version UID for this serializable class.
   */
  private static final long serialVersionUID = 5423754545363349200L;



  /**
   * Creates a new instance of this control.  It will not be considered
   * critical.
   */
  public GetPasswordPolicyStateIssuesRequestControl()
  {
    this(false);
  }



  /**
   * Creates a new instance of this control with the specified criticality.
   *
   * @param  isCritical  Indicates whether the control should be considered
   *                     critical.
   */
  public GetPasswordPolicyStateIssuesRequestControl(final boolean isCritical)
  {
    super(GET_PASSWORD_POLICY_STATE_ISSUES_REQUEST_OID, isCritical);
  }



  /**
   * Creates a new instance of this control that is decoded from the provided
   * generic control.
   *
   * @param  control  The control to decode as a get password policy state
   *                  issues request control.
   *
   * @throws LDAPException  If a problem is encountered while attempting to
   *                         decode the provided control as a get password
   *                         policy state issues request control.
   */
  public GetPasswordPolicyStateIssuesRequestControl(final Control control)
         throws LDAPException
  {
    super(control);

    if (control.hasValue())
    {
      throw new LDAPException(ResultCode.DECODING_ERROR,
           ERR_GET_PWP_STATE_ISSUES_REQUEST_HAS_VALUE.get());
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getControlName()
  {
    return INFO_CONTROL_NAME_GET_PWP_STATE_ISSUES_REQUEST.get();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void toString(final StringBuilder buffer)
  {
    buffer.append("GetPasswordPolicyStateIssuesRequestControl(isCritical=");
    buffer.append(isCritical());
    buffer.append(')');
  }
}
