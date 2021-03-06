## This file is part of the rJava package - low-level R/Java interface
## (C)2006 Simon Urbanek <simon.urbanek@r-project.org>
## (C)2018, Oracle and/or its affiliates.
## For license terms see DESCRIPTION and/or LICENSE
##
## $Id$

.onLoad <- function(libname, pkgname) {
  # FASTR <<<<<
  #Sys.setenv("LD_LIBRARY_PATH"=paste(Sys.getenv("LD_LIBRARY_PATH"),"@JAVA_LD@",sep=':'))
  ## On OS X with Oracle Java we may need to work around Oracle bug:
  ## https://bugs.openjdk.java.net/browse/JDK-7131356
  #if (length(grep("^darwin", R.version$os)) && file.exists("/usr/libexec/java_home")) {
  #   jh <- Sys.getenv("JAVA_HOME")
  #   if (!nzchar(jh)) jh <- system("/usr/libexec/java_home", intern=TRUE)[1L]
  #   if (file.exists(file.path(jh, "jre/lib"))) jh <- file.path(jh, "jre")
  #   if (file.exists(jli <- file.path(jh, "lib/jli/libjli.dylib"))) {
  #     dyn.load(jli, FALSE)
  #     dlp <- Sys.getenv("DYLD_LIBRARY_PATH")
  #     if (nzchar(dlp)) dlp <- paste0(":", dlp)
  #     if (file.exists(file.path(jh, "lib/server/libjvm.dylib")))
  #       Sys.setenv(DYLD_LIBRARY_PATH=paste0(file.path(jh, "lib/server"), dlp))
  #   }
  #}
  #library.dynam("rJava", pkgname, libname)
  # FASTR >>>>>
  # pass on to the system-independent part
  .jfirst(libname, pkgname)
}
