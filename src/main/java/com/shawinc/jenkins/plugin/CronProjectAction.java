/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.shawinc.jenkins.plugin;

import antlr.ANTLRException;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author gcampb2
 */
public class CronProjectAction implements Action {

  private static final String CRON_EXPRESSION_COMMENT_START = "#";
  private static final String CRON_EXPRESSION_COMMENT_COLOR = "#4a7b4a";
  public AbstractProject project;

  public String getIconFileName() {
    return null;
  }

  public String getDisplayName() {
    return "";
  }

  public String getUrlName() {
    return "projectCronAction";
  }

  @SuppressWarnings("rawtypes")
  public CronProjectAction(AbstractProject project) {
    this.project = project;
  }

  public Map<String, Trigger> getTriggers() throws ANTLRException {
    Map<TriggerDescriptor, Trigger> triggers = project.getTriggers();
    Map<String, Trigger> triggersOut = new HashMap<String, Trigger>();

    if (triggers != null && triggers.size() > 0) {
      for (Trigger trig : triggers.values()) {
        triggersOut.put(getTriggerName(trig), trig);
      }
    }

    if (project.getTrigger(TimerTrigger.class) == null) {
      triggersOut.put("Build periodically", new TimerTrigger(""));
    }

    return triggersOut;
  }

  public final void doUpdate(StaplerRequest req, StaplerResponse rsp)
          throws ServletException, IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ANTLRException {
    Map<String, Object> params = req.getParameterMap();
    Set<String> keyCopy = new HashSet<String>(params.keySet());

    Map<TriggerDescriptor, Trigger> triggers = project.getTriggers();

    for (Trigger trig : triggers.values()) {
      String className = trig.getClass().getName();
      Object tmp = params.get(className);

      if (tmp instanceof String[] && ((String[]) tmp).length == 1) {
        keyCopy.remove(className);
        String newSpec = ((String[]) tmp)[0];
        replaceTrigger(trig, newSpec);
      }
    }

    if (!keyCopy.isEmpty()) {
      Iterator<String> itr = keyCopy.iterator();
      while (itr.hasNext()) {
        String className = itr.next();
        Object tmp = params.get(className);
        if (className.indexOf('.') > -1 && tmp instanceof String[] && ((String[]) tmp).length == 1) {
          try {
            Class clazz = Class.forName(className);
            createTrigger(clazz, ((String[]) tmp)[0]);
          } catch (ClassNotFoundException ex) {
            // yes, we're ignoring the exceptions - they came from random params that got added in somehow
          }
        }
      }
    }
    project.save();
    rsp.forwardToPreviousPage(req);
  }

  private void replaceTrigger(Trigger trig, String newSpec) throws ServletException, IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    trig.stop();
    project.removeTrigger(trig.getDescriptor());
    createTrigger(trig.getClass(), newSpec);
  }

  private void createTrigger(Class clazz, String newSpec) throws ServletException, IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    if (newSpec != null && newSpec.trim().length() > 0) {
      Constructor<?> c = clazz.getDeclaredConstructor(String.class);
      c.setAccessible(true);
      Trigger replacement = (Trigger) c.newInstance(new Object[]{newSpec});
      replacement.start(project, true);
      project.addTrigger(replacement);
    }
  }

  private boolean hasScm() {
    SCM sourceCodeManagement = project.getScm();
    return (sourceCodeManagement != null && !(sourceCodeManagement instanceof NullSCM));
  }

  public String getCronTrigger() {

    StringBuilder expression = new StringBuilder();

    Map<TriggerDescriptor, Trigger> triggers = project.getTriggers();
    for (Trigger trigger : triggers.values()) {

      if (trigger == null) {
        continue;
      }

      String cronExpression = trigger.getSpec();
      if (cronExpression == null || cronExpression.trim().length() == 0) {
        continue;
      }

      cronExpression = formatComments(cronExpression);

      // Display each entry on a separate line.
      if (expression.length() > 0) {
        expression.append("\n<br/>\n");
      }

      // Cron expression can still be set when Source Code Management has been disabled.
      if (!hasScm() && trigger instanceof SCMTrigger) {
        expression.append("<i>(Disabled) </i>");
      }

      // Add trigger name and cron expression.
      expression.append(getTriggerName(trigger)).append(": ").append(cronExpression);
    }

    return expression.toString();
  }

  /**
   * Change the font color on the comment text within a cron expression.
   */
  private String formatComments(String cronExpression) {
    if (!cronExpression.contains(CRON_EXPRESSION_COMMENT_START)) {
      return cronExpression; // No comment found.
    }
    StringBuilder formattedExpression = new StringBuilder();

    String[] expressionLines = cronExpression.split("\n");
    for (String expressionLine : expressionLines) {
      int commentStartIndex = expressionLine.indexOf(CRON_EXPRESSION_COMMENT_START);
      if (commentStartIndex < 0) {
        // No comment, so just add the original expression line.
        formattedExpression.append(expressionLine);
      } else {
        // Comment found, wrapping comment in font tags (setting the color).
        formattedExpression.append(expressionLine.substring(0, commentStartIndex));
        formattedExpression.append("<b><i><font color=\"" + CRON_EXPRESSION_COMMENT_COLOR + "\">");
        formattedExpression.append(expressionLine.substring(commentStartIndex));
        formattedExpression.append("</font></i></b>");
      }
      formattedExpression.append(" ");
    }

    return formattedExpression.toString().trim();
  }

  /**
   * Determines the trigger name.
   *
   * @return Name of the trigger.
   */
  public String getTriggerName(Trigger<?> trigger) {
    String type = trigger.getDescriptor().getDisplayName();
    if (type == null || type.trim().length() == 0) {
      if (trigger instanceof SCMTrigger) {
        type = "SCM polling";
      } else if (trigger instanceof TimerTrigger) {
        type = "Build Trigger";
      } else {
        type = "Unknown Type";
      }
    }
    return type;
  }
}
