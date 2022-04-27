/* Generated by wayland-scanner 1.19.0 */

#ifndef WAKEFIELD_CLIENT_PROTOCOL_H
#define WAKEFIELD_CLIENT_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include "wayland-client.h"

#ifdef  __cplusplus
extern "C" {
#endif

/**
 * @page page_wakefield The wakefield protocol
 * @section page_ifaces_wakefield Interfaces
 * - @subpage page_iface_wakefield - provides capabilities necessary to for java.awt.Robot and such
 * @section page_copyright_wakefield Copyright
 * <pre>
 *
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 * </pre>
 */
struct wakefield;
struct wl_buffer;
struct wl_surface;

#ifndef WAKEFIELD_INTERFACE
#define WAKEFIELD_INTERFACE
/**
 * @page page_iface_wakefield wakefield
 * @section page_iface_wakefield_desc Description
 * @section page_iface_wakefield_api API
 * See @ref iface_wakefield.
 */
/**
 * @defgroup iface_wakefield The wakefield interface
 */
extern const struct wl_interface wakefield_interface;
#endif

#ifndef WAKEFIELD_ERROR_ENUM
#define WAKEFIELD_ERROR_ENUM
enum wakefield_error {
	/**
	 * error code 0 reserved for the absence of error
	 */
	WAKEFIELD_ERROR_NO_ERROR = 0,
	/**
	 * supplied absolute coordinates point              outside of any output
	 */
	WAKEFIELD_ERROR_INVALID_COORDINATES = 1,
	/**
	 * the request could not be fulfilled due to memory allocation error
	 */
	WAKEFIELD_ERROR_OUT_OF_MEMORY = 2,
	/**
	 * a generic error code for internal errors
	 */
	WAKEFIELD_ERROR_INTERNAL = 3,
	/**
	 * (temporary?) color cannot be converted to RGB format
	 */
	WAKEFIELD_ERROR_FORMAT = 4,
};
#endif /* WAKEFIELD_ERROR_ENUM */

/**
 * @ingroup iface_wakefield
 * @struct wakefield_listener
 */
struct wakefield_listener {
	/**
	 * facilitates implementation of Frame.getLocation()
	 *
	 * This event reveals the absolute coordinates of the surface if
	 * error_code is zero. If error_code is non-zero, (x, y) are
	 * undefined. The surface argument always correspond to that of the
	 * get_surface_location request.
	 */
	void (*surface_location)(void *data,
				 struct wakefield *wakefield,
				 struct wl_surface *surface,
				 int32_t x,
				 int32_t y,
				 uint32_t error_code);
	/**
	 * facilitates implementation of Robot.getPixelColor()
	 *
	 * This event shows the color (24-bit, format r8g8b8) of the
	 * pixel with the given absolute coordinates. The (x, y) arguments
	 * correspond to that of the get_pixel_color request. If error_code
	 * is non-zero, the rgb argument is undefined and the error_code
	 * argument contains a code from the error enum.
	 */
	void (*pixel_color)(void *data,
			    struct wakefield *wakefield,
			    int32_t x,
			    int32_t y,
			    uint32_t rgb,
			    uint32_t error_code);
	/**
	 */
	void (*capture_ready)(void *data,
			      struct wakefield *wakefield,
			      struct wl_buffer *buffer,
			      uint32_t error_code);
};

/**
 * @ingroup iface_wakefield
 */
static inline int
wakefield_add_listener(struct wakefield *wakefield,
		       const struct wakefield_listener *listener, void *data)
{
	return wl_proxy_add_listener((struct wl_proxy *) wakefield,
				     (void (**)(void)) listener, data);
}

#define WAKEFIELD_DESTROY 0
#define WAKEFIELD_MOVE_SURFACE 1
#define WAKEFIELD_GET_SURFACE_LOCATION 2
#define WAKEFIELD_GET_PIXEL_COLOR 3
#define WAKEFIELD_CAPTURE_CREATE 4

/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_SURFACE_LOCATION_SINCE_VERSION 1
/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_PIXEL_COLOR_SINCE_VERSION 1
/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_CAPTURE_READY_SINCE_VERSION 1

/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_DESTROY_SINCE_VERSION 1
/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_MOVE_SURFACE_SINCE_VERSION 1
/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_GET_SURFACE_LOCATION_SINCE_VERSION 1
/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_GET_PIXEL_COLOR_SINCE_VERSION 1
/**
 * @ingroup iface_wakefield
 */
#define WAKEFIELD_CAPTURE_CREATE_SINCE_VERSION 1

/** @ingroup iface_wakefield */
static inline void
wakefield_set_user_data(struct wakefield *wakefield, void *user_data)
{
	wl_proxy_set_user_data((struct wl_proxy *) wakefield, user_data);
}

/** @ingroup iface_wakefield */
static inline void *
wakefield_get_user_data(struct wakefield *wakefield)
{
	return wl_proxy_get_user_data((struct wl_proxy *) wakefield);
}

static inline uint32_t
wakefield_get_version(struct wakefield *wakefield)
{
	return wl_proxy_get_version((struct wl_proxy *) wakefield);
}

/**
 * @ingroup iface_wakefield
 */
static inline void
wakefield_destroy(struct wakefield *wakefield)
{
	wl_proxy_marshal((struct wl_proxy *) wakefield,
			 WAKEFIELD_DESTROY);

	wl_proxy_destroy((struct wl_proxy *) wakefield);
}

/**
 * @ingroup iface_wakefield
 *
 * This instructs the window manager to position the given wl_surface
 * at the given absolute coordinates. The subsequent get_surface_location
 * request will return these coordinates unless the surface was moved by
 * a third party.
 */
static inline void
wakefield_move_surface(struct wakefield *wakefield, struct wl_surface *surface, int32_t x, int32_t y)
{
	wl_proxy_marshal((struct wl_proxy *) wakefield,
			 WAKEFIELD_MOVE_SURFACE, surface, x, y);
}

/**
 * @ingroup iface_wakefield
 *
 * This requests a surface_location event for the given surface.
 */
static inline void
wakefield_get_surface_location(struct wakefield *wakefield, struct wl_surface *surface)
{
	wl_proxy_marshal((struct wl_proxy *) wakefield,
			 WAKEFIELD_GET_SURFACE_LOCATION, surface);
}

/**
 * @ingroup iface_wakefield
 *
 * This requests a pixel_color event at the given absolute coordinates.
 */
static inline void
wakefield_get_pixel_color(struct wakefield *wakefield, int32_t x, int32_t y)
{
	wl_proxy_marshal((struct wl_proxy *) wakefield,
			 WAKEFIELD_GET_PIXEL_COLOR, x, y);
}

/**
 * @ingroup iface_wakefield
 */
static inline void
wakefield_capture_create(struct wakefield *wakefield, struct wl_buffer *buffer, int32_t x, int32_t y)
{
	wl_proxy_marshal((struct wl_proxy *) wakefield,
			 WAKEFIELD_CAPTURE_CREATE, buffer, x, y);
}

#ifdef  __cplusplus
}
#endif

#endif
