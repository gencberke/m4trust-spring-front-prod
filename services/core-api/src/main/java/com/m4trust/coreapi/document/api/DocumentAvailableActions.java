package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;

record DocumentAvailableActions(boolean canFinalize, boolean canDownload) {}
