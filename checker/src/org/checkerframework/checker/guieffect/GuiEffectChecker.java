package org.checkerframework.checker.guieffect;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.source.SupportedLintOptions;
import org.checkerframework.framework.source.SupportedOptions;

/** @checker_framework.manual #guieffect-checker GUI Effect Checker */
@StubFiles({"org-eclipse.astub", "org-osgi.astub", "org-swtchart.astub"})
@SupportedLintOptions({"debugSpew"})
@SupportedOptions({"flagUIfields"})
public class GuiEffectChecker extends BaseTypeChecker {}
