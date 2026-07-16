/**
 * Owns the Deal aggregate, its lifecycle rules, participant-scoped access, and
 * Deal persistence.
 *
 * <p>Persistence records and repositories remain internal to this module.
 * Other modules collaborate through stable identifiers or explicit ports as
 * later slices require them.
 */
package com.m4trust.coreapi.deal;
